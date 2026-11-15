package org.soarin.timesheet.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.soarin.timesheet.student.Student;
import org.soarin.timesheet.student.StudentRepository;
import org.soarin.timesheet.timelog.TimeEntry;
import org.soarin.timesheet.timelog.TimeEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for tests that need the whole app running against a real database.
 *
 * <p>Starts the full Spring application once (Spring caches it between test classes that share
 * this setup) with the midnight scheduler turned off, so only the test decides when things
 * happen. Every test starts with empty tables.
 *
 * <p>Helpers take <b>New York local times</b> because that's how humans think about the shop
 * ("signed in at 9 PM"); they're converted to instants for the app.
 */
@SpringBootTest(properties = "soarin.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected StudentRepository students;

    @Autowired
    protected TimeEntryRepository entries;

    @Autowired
    private JdbcTemplate jdbc;

    /** Runs before every test: empty tables and a known starting time (a plain winter weekday). */
    @BeforeEach
    protected void resetDatabaseAndClock() {
        jdbc.execute("TRUNCATE time_entry, student RESTART IDENTITY CASCADE");
        setNow("2026-01-15T15:00");
    }

    /** Converts a New York wall-clock time to an instant (not for the ambiguous fall-back hour). */
    protected static Instant newYork(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(NEW_YORK).toInstant();
    }

    /** Sets "now" to a New York wall-clock time, e.g. {@code "2026-11-15T21:00"}. */
    protected void setNow(String newYorkLocalDateTime) {
        clock.set(newYork(newYorkLocalDateTime));
    }

    protected Student createStudent(String studentNumber, String firstName, String lastName) {
        return students.save(new Student(studentNumber, firstName, lastName));
    }

    /** All time entries, oldest first. */
    protected List<TimeEntry> allEntries() {
        return entries.findAll().stream()
                .sorted(Comparator.comparing(TimeEntry::getSignedInAt))
                .toList();
    }
}
