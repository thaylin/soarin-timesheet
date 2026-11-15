package org.soarin.timesheet.kiosk;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soarin.timesheet.config.ShopProperties;
import org.soarin.timesheet.student.Student;
import org.soarin.timesheet.student.StudentRepository;
import org.soarin.timesheet.time.ShopCalendar;
import org.soarin.timesheet.timelog.AutoCloseService;
import org.soarin.timesheet.timelog.TimeEntry;
import org.soarin.timesheet.timelog.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The business logic behind the kiosk: turns one scan into a sign-in or sign-out.
 *
 * <h2>The rules, in the order they are applied</h2>
 * <ol>
 *   <li><b>Clean up the input.</b> Trim spaces and upper-case it (see
 *       {@link Student#normalizeStudentNumber(String)}). If it doesn't look like an ID
 *       (letters, digits and dashes, at most 32 characters) treat it as unknown.</li>
 *   <li><b>Unknown ID</b> (or a deactivated student): record nothing, return
 *       {@link ScanOutcome#UNKNOWN_ID}.</li>
 *   <li><b>Left open from a previous day?</b> If the student's open entry started before today's
 *       midnight (e.g. the Pi was off when the midnight job should have run), auto-close it at
 *       that midnight first, exactly like {@link AutoCloseService} would have.</li>
 *   <li><b>Duplicate scan?</b> If the student's last real scan was less than 60 seconds ago
 *       (configurable: {@code soarin.duplicate-window}), record nothing and say "already signed
 *       in/out". Automatic midnight closes are not scans and don't count.</li>
 *   <li><b>Toggle.</b> Open entry → close it (sign out). No open entry → open one (sign in).</li>
 *   <li><b>Today's total.</b> Add up the parts of all of the student's entries that fall inside
 *       today's shop day, counting an open entry up to "now".</li>
 * </ol>
 *
 * <h2>Spring notes for maintainers</h2>
 * <ul>
 *   <li>{@code @Service} tells Spring to create one instance of this class at startup and pass
 *       it to anything that asks for it in its constructor ("dependency injection").</li>
 *   <li>{@code @Transactional} on {@link #scan(String)} means every database read and write in
 *       that method happens in one transaction: either all of it is saved, or (if an exception
 *       is thrown) none of it is. Changes to loaded entities (like calling
 *       {@code entry.closeByScan(now)}) are saved automatically when the method returns - you
 *       don't need to call {@code save()} for them.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * Two scans of the same badge can arrive at nearly the same moment (a double beep). The student
 * row is locked with {@code SELECT ... FOR UPDATE} (see
 * {@link StudentRepository#findActiveByStudentNumberForUpdate(String)}), so the second scan waits
 * for the first to finish and then sees it as a duplicate. As a last line of defence, the
 * database itself refuses to store two open entries for one student (a partial unique index in
 * {@code V1__init.sql}).
 */
@Service
public class KioskService {

    private static final Logger log = LoggerFactory.getLogger(KioskService.class);

    /**
     * What a plausible (already normalised) student ID looks like. Anything else is rejected
     * before we even query the database. Loosen this if the school's ID format changes.
     */
    private static final Pattern PLAUSIBLE_ID =
            Pattern.compile("[A-Z0-9-]{1," + Student.MAX_STUDENT_NUMBER_LENGTH + "}");

    private final StudentRepository students;
    private final TimeEntryRepository entries;
    private final AutoCloseService autoClose;
    private final ShopCalendar calendar;
    private final ShopProperties properties;
    private final Clock clock;

    /** Spring calls this constructor and supplies each argument automatically (dependency injection). */
    public KioskService(StudentRepository students,
                        TimeEntryRepository entries,
                        AutoCloseService autoClose,
                        ShopCalendar calendar,
                        ShopProperties properties,
                        Clock clock) {
        this.students = students;
        this.entries = entries;
        this.autoClose = autoClose;
        this.calendar = calendar;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Handles one scan. See the class documentation for the exact rules.
     *
     * @param rawStudentId exactly what the scanner (or keyboard) typed; may be {@code null} or junk
     * @return what happened, with only the information the kiosk is allowed to display
     */
    @Transactional
    public ScanResult scan(String rawStudentId) {
        // 1. Normalise and sanity-check the input.
        String studentNumber = Student.normalizeStudentNumber(rawStudentId);
        if (!PLAUSIBLE_ID.matcher(studentNumber).matches()) {
            log.debug("Kiosk scan rejected: input is not a plausible student ID");
            return ScanResult.unknown();
        }

        // 2. Look the student up (and lock their row until this transaction ends).
        Optional<Student> found = students.findActiveByStudentNumberForUpdate(studentNumber);
        if (found.isEmpty()) {
            // Never log the ID itself - it identifies a student (or reveals a typo of one).
            log.debug("Kiosk scan: no active student matched");
            return ScanResult.unknown();
        }
        Student student = found.get();

        // Read the clock exactly once so every decision below uses the same "now".
        Instant now = clock.instant();
        Optional<TimeEntry> latest = entries.findFirstByStudentIdOrderBySignedInAtDesc(student.getId());

        // 3. An entry left open from an earlier day gets closed at that day's midnight first.
        if (latest.isPresent() && autoClose.isStale(latest.get(), now)) {
            autoClose.autoClose(latest.get());
            // Write the close to the database right now. Without this, Hibernate would send the
            // INSERT for today's new entry *before* this UPDATE (it orders inserts first), and the
            // database's "only one open entry per student" index would reject the insert.
            entries.flush();
        }

        // 4. Duplicate-scan suppression.
        if (latest.isPresent() && isWithinDuplicateWindow(latest.get().lastScanAt(), now)) {
            TimeEntry last = latest.get();
            ScanOutcome outcome = last.isOpen() ? ScanOutcome.ALREADY_SIGNED_IN : ScanOutcome.ALREADY_SIGNED_OUT;
            log.debug("Kiosk scan ignored as duplicate (entry {})", last.getId());
            return result(outcome, student, now);
        }

        // 5. Toggle.
        if (latest.isPresent() && latest.get().isOpen()) {
            TimeEntry open = latest.get();
            open.closeByScan(now); // saved automatically at the end of the transaction
            log.debug("Kiosk sign-out recorded (entry {})", open.getId());
            return result(ScanOutcome.SIGNED_OUT, student, now);
        }
        TimeEntry created = entries.save(new TimeEntry(student, now));
        log.debug("Kiosk sign-in recorded (entry {})", created.getId());
        return result(ScanOutcome.SIGNED_IN, student, now);
    }

    /**
     * True if {@code lastScan} happened less than the duplicate window before {@code now}.
     * Exactly 60 seconds later counts as a new scan. A last scan in the "future" (which can only
     * happen if the system clock was moved backwards) is also treated as a duplicate, which is
     * the safe choice: it records nothing.
     */
    private boolean isWithinDuplicateWindow(Instant lastScan, Instant now) {
        return Duration.between(lastScan, now).compareTo(properties.duplicateWindow()) < 0;
    }

    /** Builds the kiosk result, including the student's total time for today. */
    private ScanResult result(ScanOutcome outcome, Student student, Instant now) {
        return new ScanResult(outcome, student.kioskDisplayName(), todayTotal(student, now));
    }

    /**
     * Total time the student has spent in the shop during the current shop day. Entries that
     * started yesterday (before midnight) only count from midnight; an open entry counts up to
     * {@code now}.
     */
    private Duration todayTotal(Student student, Instant now) {
        LocalDate today = calendar.shopDate(now);
        Duration total = Duration.ZERO;
        for (TimeEntry entry : entries.findOverlapping(
                student.getId(), calendar.startOfDay(today), calendar.endOfDay(today))) {
            Instant end = entry.isOpen() ? now : entry.getSignedOutAt();
            total = total.plus(calendar.overlapWithDay(entry.getSignedInAt(), end, today));
        }
        return total;
    }
}
