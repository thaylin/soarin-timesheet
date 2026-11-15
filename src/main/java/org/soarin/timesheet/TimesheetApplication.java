package org.soarin.timesheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Soarin shop time log.
 *
 * <p>{@code @SpringBootApplication} turns on Spring Boot's auto-configuration and makes Spring
 * scan this package and every package below it ({@code org.soarin.timesheet.*}) for classes
 * annotated with {@code @Component}, {@code @Service}, {@code @RestController},
 * {@code @Configuration}, and so on. New classes must live under this package to be found.
 *
 * <p>Start here when learning the code; {@code docs/ARCHITECTURE.md} gives the full tour.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TimesheetApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimesheetApplication.class, args);
    }
}
