package org.soarin.timesheet.timelog;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soarin.timesheet.time.ShopCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes time entries left open past the end of their shop day. Each such entry is closed at
 * local midnight ending the day it was opened, marked {@link CloseReason#AUTO_MIDNIGHT} and
 * flagged {@code needs_review} so an admin can correct the real sign-out time later.
 *
 * <p>Idempotent: safe to run at any time and as often as wanted.
 */
@Service
public class AutoCloseService {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseService.class);

    private final TimeEntryRepository entries;
    private final ShopCalendar calendar;
    private final Clock clock;

    public AutoCloseService(TimeEntryRepository entries, ShopCalendar calendar, Clock clock) {
        this.entries = entries;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Auto-closes every entry opened before the start of the current shop day. Returns how many were closed. */
    @Transactional
    public int closeEntriesFromPreviousDays() {
        Instant startOfToday = calendar.startOfDay(calendar.shopDate(clock.instant()));
        List<TimeEntry> stale = entries.findOpenStartedBefore(startOfToday);
        stale.forEach(this::autoClose);
        if (!stale.isEmpty()) {
            // Counts only - no student identifiers in logs.
            log.info("Auto-closed {} time entr{} left open at end of shop day", stale.size(),
                    stale.size() == 1 ? "y" : "ies");
        }
        return stale.size();
    }

    /** Auto-closes one entry at the end of the shop day it was opened on. Caller provides the transaction. */
    public void autoClose(TimeEntry entry) {
        entry.autoClose(calendar.endOfDay(calendar.shopDate(entry.getSignedInAt())));
    }

    /** True if the entry is open but was started on an earlier shop day than {@code now}. */
    public boolean isStale(TimeEntry entry, Instant now) {
        return entry.isOpen() && entry.getSignedInAt().isBefore(calendar.startOfDay(calendar.shopDate(now)));
    }
}
