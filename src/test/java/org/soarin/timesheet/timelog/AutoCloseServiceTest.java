package org.soarin.timesheet.timelog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soarin.timesheet.student.Student;
import org.soarin.timesheet.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the end-of-day auto sign-out ({@link AutoCloseService}), including the
 * 2026 daylight-saving changes (spring forward 2026-03-08, fall back 2026-11-01).
 *
 * <p>Each test creates entries directly, moves the clock to just after midnight, runs the job,
 * and checks the entries were closed at exactly local midnight and flagged for review.
 */
class AutoCloseServiceTest extends IntegrationTest {

    @Autowired
    private AutoCloseService autoClose;

    private Student ada;
    private Student grace;

    @BeforeEach
    void addStudents() {
        ada = createStudent("1001", "Ada", "Lovelace");
        grace = createStudent("1002", "Grace", "Hopper");
    }

    private TimeEntry openEntry(Student student, Instant signedInAt) {
        return entries.save(new TimeEntry(student, signedInAt));
    }

    private TimeEntry reload(TimeEntry entry) {
        return entries.findById(entry.getId()).orElseThrow();
    }

    @Test
    void entryStillOpenAtMidnightIsClosedAtMidnightAndFlaggedForReview() {
        TimeEntry entry = openEntry(ada, newYork("2026-11-14T20:00"));
        setNow("2026-11-15T00:00:05");

        int closed = autoClose.closeEntriesFromPreviousDays();

        assertThat(closed).isEqualTo(1);
        TimeEntry after = reload(entry);
        assertThat(after.getSignedOutAt()).isEqualTo(Instant.parse("2026-11-15T05:00:00Z")); // 00:00 EST
        assertThat(after.getCloseReason()).isEqualTo(CloseReason.AUTO_MIDNIGHT);
        assertThat(after.isNeedsReview()).isTrue();
    }

    @Test
    void entriesFromTodayAndClosedEntriesAreLeftAlone() {
        TimeEntry todayOpen = openEntry(ada, newYork("2026-11-15T00:00:01"));
        TimeEntry yesterdayClosed = openEntry(grace, newYork("2026-11-14T15:00"));
        yesterdayClosed.closeByScan(newYork("2026-11-14T17:00"));
        entries.save(yesterdayClosed);
        setNow("2026-11-15T00:00:05");

        assertThat(autoClose.closeEntriesFromPreviousDays()).isZero();

        assertThat(reload(todayOpen).isOpen()).isTrue();
        TimeEntry stillScanClosed = reload(yesterdayClosed);
        assertThat(stillScanClosed.getCloseReason()).isEqualTo(CloseReason.SCAN);
        assertThat(stillScanClosed.isNeedsReview()).isFalse();
    }

    @Test
    void runningAgainDoesNothing() {
        openEntry(ada, newYork("2026-11-14T20:00"));
        setNow("2026-11-15T00:00:05");
        autoClose.closeEntriesFromPreviousDays();

        setNow("2026-11-15T01:00:05");
        assertThat(autoClose.closeEntriesFromPreviousDays()).isZero();
    }

    @Test
    void closesEveryStudentsOpenEntry() {
        openEntry(ada, newYork("2026-11-14T18:00"));
        openEntry(grace, newYork("2026-11-14T19:00"));
        setNow("2026-11-15T00:00:05");

        assertThat(autoClose.closeEntriesFromPreviousDays()).isEqualTo(2);
        assertThat(allEntries()).allSatisfy(e -> assertThat(e.getCloseReason()).isEqualTo(CloseReason.AUTO_MIDNIGHT));
    }

    @Test
    void entryOpenedJustBeforeMidnightIsClosedAtMidnight() {
        TimeEntry entry = openEntry(ada, newYork("2026-11-14T23:59:59"));
        setNow("2026-11-15T00:00:05");

        autoClose.closeEntriesFromPreviousDays();

        assertThat(reload(entry).getSignedOutAt()).isEqualTo(newYork("2026-11-15T00:00"));
    }

    @Test
    void ifThePiWasOffForDaysEntryIsClosedAtTheMidnightEndingItsOwnDay() {
        // Signed in Friday evening, job doesn't run until Monday morning.
        TimeEntry entry = openEntry(ada, newYork("2026-11-13T18:00"));
        setNow("2026-11-16T08:00");

        autoClose.closeEntriesFromPreviousDays();

        assertThat(reload(entry).getSignedOutAt()).isEqualTo(newYork("2026-11-14T00:00"));
    }

    // ---- Daylight saving time -------------------------------------------------------------

    @Test
    void springForwardNightClosesAtMidnightEst() {
        // Night of Sat Mar 7 -> Sun Mar 8: midnight is still EST (UTC-5); the jump is at 2 AM.
        TimeEntry entry = openEntry(ada, newYork("2026-03-07T20:00"));
        setNow("2026-03-08T00:00:05");

        autoClose.closeEntriesFromPreviousDays();

        assertThat(reload(entry).getSignedOutAt()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
    }

    @Test
    void dayAfterSpringForwardClosesAtMidnightEdt() {
        // Sun Mar 8 is a 23-hour day; its midnight end is EDT (UTC-4).
        TimeEntry entry = openEntry(ada, newYork("2026-03-08T20:00"));
        setNow("2026-03-09T00:00:05");

        autoClose.closeEntriesFromPreviousDays();

        TimeEntry after = reload(entry);
        assertThat(after.getSignedOutAt()).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
        assertThat(after.getSignedInAt()).isEqualTo(Instant.parse("2026-03-09T00:00:00Z")); // 8 PM EDT
    }

    @Test
    void fallBackNightClosesAtMidnightEdt() {
        // Night of Sat Oct 31 -> Sun Nov 1: midnight is still EDT (UTC-4); clocks go back at 2 AM.
        TimeEntry entry = openEntry(ada, newYork("2026-10-31T20:00"));
        setNow("2026-11-01T00:00:05");

        autoClose.closeEntriesFromPreviousDays();

        assertThat(reload(entry).getSignedOutAt()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
    }

    @Test
    void dayAfterFallBackClosesAtMidnightEst() {
        // Sun Nov 1 is a 25-hour day; its midnight end is EST (UTC-5).
        TimeEntry entry = openEntry(ada, newYork("2026-11-01T20:00"));
        setNow("2026-11-02T00:00:05");

        autoClose.closeEntriesFromPreviousDays();

        assertThat(reload(entry).getSignedOutAt()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    }

    @Test
    void entryStartedDuringTheRepeatedFallBackHourIsNotClosedEarly() {
        // 1:30 AM EST (the *second* 1:30 that night) on Nov 1 is 06:30 UTC - still Nov 1.
        TimeEntry entry = openEntry(ada, Instant.parse("2026-11-01T06:30:00Z"));
        clock.set(Instant.parse("2026-11-01T23:00:00Z")); // 6 PM EST Nov 1

        assertThat(autoClose.closeEntriesFromPreviousDays()).isZero();
        assertThat(reload(entry).isOpen()).isTrue();

        List<TimeEntry> stillOpen = entries.findOpenStartedBefore(Instant.parse("2026-11-02T05:00:00Z"));
        assertThat(stillOpen).hasSize(1);
    }
}
