package org.soarin.timesheet.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Converts between stored instants (UTC) and the shop's local calendar day.
 *
 * <p>A shop day runs from local midnight to the next local midnight in the configured zone
 * (America/New_York by default), so it is 23 hours long on the spring-forward DST day and
 * 25 hours long on the fall-back day. Day ranges are half-open: {@code [startOfDay, endOfDay)}.
 */
public final class ShopCalendar {

    private final ZoneId zone;

    public ShopCalendar(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public ZoneId zone() {
        return zone;
    }

    /** The local shop date on which the given instant falls. */
    public LocalDate shopDate(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    /** First instant of the given shop day (local midnight). */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Exclusive end of the given shop day, i.e. the next local midnight. */
    public Instant endOfDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }

    /** How much of {@code [start, end)} falls inside the given shop day. */
    public Duration overlapWithDay(Instant start, Instant end, LocalDate date) {
        Instant dayStart = startOfDay(date);
        Instant dayEnd = endOfDay(date);
        Instant from = start.isAfter(dayStart) ? start : dayStart;
        Instant to = end.isBefore(dayEnd) ? end : dayEnd;
        return to.isAfter(from) ? Duration.between(from, to) : Duration.ZERO;
    }
}
