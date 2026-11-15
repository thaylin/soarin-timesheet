package org.soarin.timesheet.config;

import java.time.Clock;
import org.soarin.timesheet.time.ShopCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the time-related objects ("beans") the rest of the app uses.
 *
 * <p><b>Spring notes:</b> a {@code @Configuration} class holds {@code @Bean} methods. Spring calls
 * each one once at startup and hands the returned object to any class that asks for that type in
 * its constructor. That is how {@code KioskService} gets a {@link Clock} without creating one.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /**
     * The single source of "now" for the whole app.
     *
     * <p><b>Rule:</b> never call {@code Instant.now()} or {@code LocalDate.now()} in business
     * logic - inject this {@link Clock} and call {@code clock.instant()}. That lets the tests
     * replace it with a fake clock and check things like "what happens at midnight on the DST
     * change day" without waiting for that day. The clock is UTC; convert to shop-local time
     * only through {@link ShopCalendar}.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** The shop's local calendar (America/New_York unless {@code soarin.zone} says otherwise). */
    @Bean
    public ShopCalendar shopCalendar(ShopProperties properties) {
        return new ShopCalendar(properties.zone());
    }
}
