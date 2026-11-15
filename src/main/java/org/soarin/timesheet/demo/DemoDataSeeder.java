package org.soarin.timesheet.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soarin.timesheet.student.Student;
import org.soarin.timesheet.student.StudentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds three obviously fake students so you can try the kiosk without real data.
 *
 * <p>Only active with the {@code demo} Spring profile ({@code SPRING_PROFILES_ACTIVE=demo} in
 * {@code .env}), and only when the roster is completely empty. Don't use it on the real Pi.
 *
 * <p>Demo IDs: {@code DEMO-1001}, {@code DEMO-1002}, {@code DEMO-1003}.
 *
 * <p>({@code ApplicationRunner} beans run once, right after the application has started.)
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final StudentRepository students;

    public DemoDataSeeder(StudentRepository students) {
        this.students = students;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (students.count() > 0) {
            log.info("Demo profile active but the roster is not empty; not adding demo students");
            return;
        }
        students.save(new Student("DEMO-1001", "Ada", "Lovelace"));
        students.save(new Student("DEMO-1002", "Grace", "Hopper"));
        students.save(new Student("DEMO-1003", "Alan", "Turing"));
        log.info("Demo profile: added 3 fake students (IDs DEMO-1001..DEMO-1003)");
    }
}
