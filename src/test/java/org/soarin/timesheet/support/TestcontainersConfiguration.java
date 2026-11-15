package org.soarin.timesheet.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Extra beans for integration tests.
 *
 * <ul>
 *   <li><b>A real PostgreSQL</b> in a throwaway Docker container (Testcontainers), the same
 *       version as in {@code docker-compose.yml}. {@code @ServiceConnection} tells Spring Boot to
 *       point the app's datasource at it automatically. Using real PostgreSQL (rather than an
 *       in-memory imitation) means the Flyway migration, the partial unique index and
 *       {@code timestamptz} behaviour are all tested for real. <b>Docker must be running</b> to
 *       run the tests.</li>
 *   <li><b>A {@link MutableClock}</b> marked {@code @Primary}, so it is injected wherever the app
 *       asks for a {@code Clock} instead of the real system clock.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-15T15:00:00Z"));
    }
}
