package org.soarin.timesheet.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} that tests can set and move forward by hand. It replaces the real clock in
 * integration tests (see {@link TestcontainersConfiguration}), which lets a test say "it is now
 * 23:59 on the night the clocks change" and check what the app does.
 */
public class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    /** Jump to an exact moment. */
    public void set(Instant instant) {
        this.now = instant;
    }

    /** Move forward (or backward, with a negative duration). */
    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // The app only ever asks for instants, so a zone change has no effect on behaviour.
        return this;
    }
}
