package org.soarin.timesheet.timelog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link AutoCloseService}. It runs a few seconds after every hour in shop-local time:
 * the 00:00:05 run is the "midnight auto sign-out", and the other runs (plus one at startup)
 * catch up if the Pi was off or its clock jumped at midnight. Entries are always closed at the
 * midnight that ended their own day, regardless of when the job actually runs.
 *
 * <p><b>Spring notes:</b> {@code @Scheduled(cron = ...)} makes Spring call the method on a
 * timer. The cron expression has six fields - second, minute, hour, day-of-month, month,
 * day-of-week - and {@code zone} makes it follow New York local time (including DST). Both
 * values come from {@code application.yml} via {@code ${...}} placeholders. Scheduling only
 * works because {@link org.soarin.timesheet.config.SchedulingConfig} has
 * {@code @EnableScheduling}. {@code @EventListener(ApplicationReadyEvent.class)} runs a method
 * once when the app has finished starting.
 *
 * <p>{@code @ConditionalOnProperty} means this bean isn't created at all when
 * {@code soarin.scheduling.enabled=false} - the tests use that so the job can't run
 * unexpectedly in the middle of a test.
 *
 * <p><b>Raspberry Pi clock warning:</b> a Pi has no battery-backed clock. If it can't reach a
 * time server after a reboot, its clock may be wrong and so will every timestamp. See the
 * README section on time synchronisation.
 */
@Component
@ConditionalOnProperty(name = "soarin.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AutoCloseScheduler {

    private final AutoCloseService autoCloseService;

    public AutoCloseScheduler(AutoCloseService autoCloseService) {
        this.autoCloseService = autoCloseService;
    }

    @Scheduled(cron = "${soarin.auto-close.cron:5 0 * * * *}", zone = "${soarin.zone:America/New_York}")
    public void hourly() {
        autoCloseService.closeEntriesFromPreviousDays();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        autoCloseService.closeEntriesFromPreviousDays();
    }
}
