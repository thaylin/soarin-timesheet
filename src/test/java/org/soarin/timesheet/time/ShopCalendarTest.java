package org.soarin.timesheet.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests (no Spring, no database) for shop-day calculations in America/New_York,
 * including both 2026 daylight-saving changes:
 * <ul>
 *   <li>Spring forward: Sunday 2026-03-08, 2:00 AM EST jumps to 3:00 AM EDT (23-hour day).</li>
 *   <li>Fall back: Sunday 2026-11-01, 2:00 AM EDT goes back to 1:00 AM EST (25-hour day).</li>
 * </ul>
 * New York is UTC-5 in winter (EST) and UTC-4 in summer (EDT).
 */
class ShopCalendarTest {

    private final ShopCalendar calendar = new ShopCalendar(ZoneId.of("America/New_York"));

    @Nested
    class ShopDate {

        @Test
        void winterDayChangesAtFiveAmUtc() {
            assertThat(calendar.shopDate(Instant.parse("2026-11-16T04:59:59Z"))).isEqualTo("2026-11-15");
            assertThat(calendar.shopDate(Instant.parse("2026-11-16T05:00:00Z"))).isEqualTo("2026-11-16");
        }

        @Test
        void summerDayChangesAtFourAmUtc() {
            assertThat(calendar.shopDate(Instant.parse("2026-07-01T03:59:59Z"))).isEqualTo("2026-06-30");
            assertThat(calendar.shopDate(Instant.parse("2026-07-01T04:00:00Z"))).isEqualTo("2026-07-01");
        }

        @Test
        void eveningInNewYorkIsStillTheSameShopDayEvenThoughUtcIsTomorrow() {
            // 11:30 PM EST on Nov 15 is already Nov 16 in UTC - the shop day must say Nov 15.
            assertThat(calendar.shopDate(Instant.parse("2026-11-16T04:30:00Z"))).isEqualTo("2026-11-15");
        }
    }

    @Nested
    class DayBoundaries {

        @Test
        void startOfDayIsLocalMidnight() {
            assertThat(calendar.startOfDay(LocalDate.parse("2026-01-15"))).isEqualTo("2026-01-15T05:00:00Z");
            assertThat(calendar.startOfDay(LocalDate.parse("2026-07-15"))).isEqualTo("2026-07-15T04:00:00Z");
        }

        @Test
        void endOfDayIsNextLocalMidnight() {
            assertThat(calendar.endOfDay(LocalDate.parse("2026-01-15"))).isEqualTo("2026-01-16T05:00:00Z");
        }

        @Test
        void normalDayIs24Hours() {
            assertThat(lengthOf("2026-01-15")).isEqualTo(Duration.ofHours(24));
        }

        @Test
        void springForwardDayIs23Hours() {
            assertThat(calendar.startOfDay(LocalDate.parse("2026-03-08"))).isEqualTo("2026-03-08T05:00:00Z"); // EST
            assertThat(calendar.endOfDay(LocalDate.parse("2026-03-08"))).isEqualTo("2026-03-09T04:00:00Z");   // EDT
            assertThat(lengthOf("2026-03-08")).isEqualTo(Duration.ofHours(23));
        }

        @Test
        void fallBackDayIs25Hours() {
            assertThat(calendar.startOfDay(LocalDate.parse("2026-11-01"))).isEqualTo("2026-11-01T04:00:00Z"); // EDT
            assertThat(calendar.endOfDay(LocalDate.parse("2026-11-01"))).isEqualTo("2026-11-02T05:00:00Z");   // EST
            assertThat(lengthOf("2026-11-01")).isEqualTo(Duration.ofHours(25));
        }

        private Duration lengthOf(String date) {
            LocalDate day = LocalDate.parse(date);
            return Duration.between(calendar.startOfDay(day), calendar.endOfDay(day));
        }
    }

    @Nested
    class Overlap {

        @Test
        void entryEntirelyInsideDayCountsFully() {
            Instant in = Instant.parse("2026-01-15T14:00:00Z");  // 9:00 AM EST
            Instant out = Instant.parse("2026-01-15T16:30:00Z"); // 11:30 AM EST
            assertThat(calendar.overlapWithDay(in, out, LocalDate.parse("2026-01-15")))
                    .isEqualTo(Duration.ofMinutes(150));
        }

        @Test
        void entryCrossingMidnightIsSplitBetweenDays() {
            Instant in = Instant.parse("2026-11-15T03:00:00Z");  // 10 PM EST Nov 14
            Instant out = Instant.parse("2026-11-15T07:00:00Z"); //  2 AM EST Nov 15
            assertThat(calendar.overlapWithDay(in, out, LocalDate.parse("2026-11-14"))).isEqualTo(Duration.ofHours(2));
            assertThat(calendar.overlapWithDay(in, out, LocalDate.parse("2026-11-15"))).isEqualTo(Duration.ofHours(2));
        }

        @Test
        void fallBackNightCountsRealElapsedTimeNotWallClockTime() {
            // 00:30 EDT to 03:00 EST looks like 2.5 hours on the wall clock, but 1:00-2:00 happens
            // twice that night, so 3.5 real hours pass. Students are credited for real time.
            Instant in = Instant.parse("2026-11-01T04:30:00Z");  // 00:30 EDT
            Instant out = Instant.parse("2026-11-01T08:00:00Z"); // 03:00 EST
            assertThat(calendar.overlapWithDay(in, out, LocalDate.parse("2026-11-01")))
                    .isEqualTo(Duration.ofMinutes(210));
        }

        @Test
        void entryOnAnotherDayContributesNothing() {
            Instant in = Instant.parse("2026-01-14T14:00:00Z");
            Instant out = Instant.parse("2026-01-14T16:00:00Z");
            assertThat(calendar.overlapWithDay(in, out, LocalDate.parse("2026-01-15"))).isZero();
        }
    }
}
