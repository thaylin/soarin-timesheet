package org.soarin.timesheet.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Shop-wide settings, read from the {@code soarin:} section of {@code application.yml}
 * (or the matching environment variables, e.g. {@code SOARIN_ZONE}).
 *
 * <p><b>Spring notes:</b> {@code @ConfigurationProperties} binds configuration keys to this
 * record's components by name ({@code duplicate-window} → {@code duplicateWindow}), converting
 * text like {@code "60s"} into a {@link Duration} and {@code "America/New_York"} into a
 * {@link ZoneId}. {@code @DefaultValue} supplies a value when the key is missing. The record is
 * registered because {@code TimesheetApplication} has {@code @ConfigurationPropertiesScan}.
 *
 * <p>Future settings (e.g. required shop hours per group) can be added here as new components.
 *
 * @param zone            time zone that defines the shop day (midnight to midnight local time)
 * @param duplicateWindow repeat scans of the same ID within this window are ignored
 */
@ConfigurationProperties(prefix = "soarin")
public record ShopProperties(
        @DefaultValue("America/New_York") ZoneId zone,
        @DefaultValue("60s") Duration duplicateWindow) {
}
