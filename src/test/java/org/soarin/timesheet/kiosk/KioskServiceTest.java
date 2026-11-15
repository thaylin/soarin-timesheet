package org.soarin.timesheet.kiosk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.soarin.timesheet.student.Student;
import org.soarin.timesheet.support.IntegrationTest;
import org.soarin.timesheet.timelog.CloseReason;
import org.soarin.timesheet.timelog.TimeEntry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the kiosk rules in {@link KioskService}, against real PostgreSQL.
 * Times in test names and comments are New York local time.
 */
class KioskServiceTest extends IntegrationTest {

    @Autowired
    private KioskService kiosk;

    @BeforeEach
    void addStudent() {
        createStudent("123456", "Ada", "Lovelace");
    }

    @Nested
    class Toggle {

        @Test
        void firstScanSignsIn() {
            setNow("2026-01-15T15:00");

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(result.displayName()).isEqualTo("Ada L.");
            assertThat(result.todayTotal()).isZero();
            List<TimeEntry> all = allEntries();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).isOpen()).isTrue();
            assertThat(all.get(0).getSignedInAt()).isEqualTo(newYork("2026-01-15T15:00"));
        }

        @Test
        void secondScanSignsOutAndReportsTodaysHours() {
            setNow("2026-01-15T15:00");
            kiosk.scan("123456");
            setNow("2026-01-15T17:15");

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_OUT);
            assertThat(result.todayTotal()).isEqualTo(Duration.ofMinutes(135));
            TimeEntry entry = allEntries().get(0);
            assertThat(entry.getSignedOutAt()).isEqualTo(newYork("2026-01-15T17:15"));
            assertThat(entry.getCloseReason()).isEqualTo(CloseReason.SCAN);
            assertThat(entry.isNeedsReview()).isFalse();
        }

        @Test
        void thirdScanSignsInAgainWithANewEntry() {
            setNow("2026-01-15T09:00");
            kiosk.scan("123456");
            setNow("2026-01-15T11:30");
            kiosk.scan("123456");
            setNow("2026-01-15T13:00");

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(allEntries()).hasSize(2);
            assertThat(result.todayTotal()).isEqualTo(Duration.ofMinutes(150));
        }

        @Test
        void todaysHoursAddUpAllEntriesAndCountTheOpenOneUntilNow() {
            setNow("2026-01-15T09:00");
            kiosk.scan("123456");                 // in
            setNow("2026-01-15T11:30");
            kiosk.scan("123456");                 // out: 2h30
            setNow("2026-01-15T13:00");
            kiosk.scan("123456");                 // in
            setNow("2026-01-15T14:00");

            ScanResult result = kiosk.scan("123456"); // out: +1h

            assertThat(result.todayTotal()).isEqualTo(Duration.ofMinutes(210));
        }

        @Test
        void studentsAreTrackedIndependently() {
            createStudent("777", "Grace", "Hopper");
            kiosk.scan("123456");

            ScanResult result = kiosk.scan("777");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(result.displayName()).isEqualTo("Grace H.");
            assertThat(allEntries()).hasSize(2);
        }

        @Test
        void scannedIdIsTrimmedAndCaseInsensitive() {
            createStudent("AB-12", "Alan", "Turing");

            ScanResult result = kiosk.scan("  ab-12\r\n");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(result.displayName()).isEqualTo("Alan T.");
        }
    }

    @Nested
    class DuplicateScans {

        @Test
        void repeatScanWithin60SecondsOfSignInIsIgnored() {
            kiosk.scan("123456");
            clock.advance(Duration.ofSeconds(59));

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.ALREADY_SIGNED_IN);
            assertThat(result.displayName()).isEqualTo("Ada L.");
            List<TimeEntry> all = allEntries();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).isOpen()).isTrue();
        }

        @Test
        void repeatScanWithin60SecondsOfSignOutIsIgnored() {
            kiosk.scan("123456");
            clock.advance(Duration.ofHours(1));
            kiosk.scan("123456");                       // signed out
            clock.advance(Duration.ofSeconds(10));

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.ALREADY_SIGNED_OUT);
            List<TimeEntry> all = allEntries();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).isOpen()).isFalse();
        }

        @Test
        void manyRapidRepeatsAreAllIgnored() {
            kiosk.scan("123456");
            for (int i = 0; i < 5; i++) {
                clock.advance(Duration.ofSeconds(5));
                assertThat(kiosk.scan("123456").outcome()).isEqualTo(ScanOutcome.ALREADY_SIGNED_IN);
            }
            assertThat(allEntries()).hasSize(1);
        }

        @Test
        void scanExactly60SecondsLaterCountsAsANewScan() {
            kiosk.scan("123456");
            clock.advance(Duration.ofSeconds(60));

            assertThat(kiosk.scan("123456").outcome()).isEqualTo(ScanOutcome.SIGNED_OUT);
        }

        @Test
        void aDuplicateFromOneStudentDoesNotAffectAnother() {
            createStudent("777", "Grace", "Hopper");
            kiosk.scan("123456");
            clock.advance(Duration.ofSeconds(5));

            assertThat(kiosk.scan("777").outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
        }
    }

    @Nested
    class UnknownIds {

        @Test
        void unknownIdRecordsNothingAndRevealsNothing() {
            ScanResult result = kiosk.scan("999999");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.UNKNOWN_ID);
            assertThat(result.displayName()).isNull();
            assertThat(result.todayTotal()).isNull();
            assertThat(entries.count()).isZero();
        }

        @Test
        void deactivatedStudentIsTreatedAsUnknown() {
            Student former = createStudent("555", "Former", "Member");
            former.setActive(false);
            students.save(former);

            assertThat(kiosk.scan("555").outcome()).isEqualTo(ScanOutcome.UNKNOWN_ID);
            assertThat(entries.count()).isZero();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "12 34", "<script>", "123456; DROP TABLE student", "12345678901234567890123456789012345"})
        void inputThatIsNotAPlausibleIdIsUnknown(String input) {
            assertThat(kiosk.scan(input).outcome()).isEqualTo(ScanOutcome.UNKNOWN_ID);
            assertThat(entries.count()).isZero();
        }
    }

    @Nested
    class DayBoundaries {

        @Test
        void todaysHoursUseTheNewYorkDayNotTheUtcDay() {
            // 6:00 PM - 11:30 PM EST on Nov 15. The sign-out is already Nov 16 in UTC.
            setNow("2026-11-15T18:00");
            kiosk.scan("123456");
            setNow("2026-11-15T23:30");
            ScanResult evening = kiosk.scan("123456");
            assertThat(evening.todayTotal()).isEqualTo(Duration.ofMinutes(330));

            // 12:30 AM EST Nov 16 is a new shop day (same UTC date as the sign-out above), so
            // last night's time must not count towards "today".
            setNow("2026-11-16T00:30");
            ScanResult afterMidnight = kiosk.scan("123456");

            assertThat(afterMidnight.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(afterMidnight.todayTotal()).isZero();
        }

        @Test
        void entryLeftOpenYesterdayIsAutoClosedAtMidnightAndTheScanSignsIn() {
            // Simulates the midnight job not having run (e.g. the Pi was switched off overnight).
            setNow("2026-11-14T16:00");
            kiosk.scan("123456");
            setNow("2026-11-15T09:00");

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(result.todayTotal()).isZero();
            List<TimeEntry> all = allEntries();
            assertThat(all).hasSize(2);
            TimeEntry yesterday = all.get(0);
            assertThat(yesterday.getSignedOutAt()).isEqualTo(Instant.parse("2026-11-15T05:00:00Z")); // midnight EST
            assertThat(yesterday.getCloseReason()).isEqualTo(CloseReason.AUTO_MIDNIGHT);
            assertThat(yesterday.isNeedsReview()).isTrue();
            assertThat(all.get(1).isOpen()).isTrue();
        }

        @Test
        void scanJustAfterAnAutomaticMidnightCloseIsNotTreatedAsADuplicate() {
            // Auto-closes are not scans, so they don't start the 60-second duplicate window.
            setNow("2026-11-14T23:50");
            kiosk.scan("123456");
            setNow("2026-11-15T00:00:30");

            ScanResult result = kiosk.scan("123456");

            assertThat(result.outcome()).isEqualTo(ScanOutcome.SIGNED_IN);
            assertThat(result.todayTotal()).isEqualTo(Duration.ZERO);
        }

        @Test
        void hoursOnTheSpringForwardDayAreRealElapsedTime() {
            // 1:30 AM EST -> 3:30 AM EDT on 2026-03-08 is only one real hour.
            setNow("2026-03-08T01:30");
            kiosk.scan("123456");
            setNow("2026-03-08T03:30");

            assertThat(kiosk.scan("123456").todayTotal()).isEqualTo(Duration.ofHours(1));
        }
    }
}
