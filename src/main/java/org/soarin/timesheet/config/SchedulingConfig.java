package org.soarin.timesheet.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (midnight auto sign-out). Disabled in tests via {@code soarin.scheduling.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "soarin.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
