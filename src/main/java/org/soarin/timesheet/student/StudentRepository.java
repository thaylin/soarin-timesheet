package org.soarin.timesheet.student;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link Student}.
 *
 * <p><b>How Spring Data repositories work:</b> this is only an interface - there is no class
 * implementing it in our code. At startup Spring Data generates the implementation. By extending
 * {@link JpaRepository} we get {@code save}, {@code findById}, {@code findAll}, {@code count},
 * {@code delete}, etc. for free. Extra queries are added either by naming a method following
 * Spring Data's conventions (e.g. {@code findByLastName(String)}), or with an explicit
 * {@code @Query} in JPQL (a SQL-like language that uses Java class/field names rather than
 * table/column names), as below.
 *
 * <p>The future admin area will add roster queries here (search, list by graduation year, ...).
 * Those must never be reachable from the kiosk controller.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Looks up an active student by normalized ID and row-locks it for the rest of the
     * transaction, so two near-simultaneous scans for the same student are serialized.
     *
     * <p>{@code @Lock(PESSIMISTIC_WRITE)} makes the generated SQL end with {@code FOR UPDATE}:
     * any other transaction that tries to lock the same row waits until ours commits. It must be
     * called inside a {@code @Transactional} method (the lock is released when that ends).
     *
     * @param studentNumber an ID already passed through {@link Student#normalizeStudentNumber(String)}
     * @return the student, or empty if there's no <em>active</em> student with that ID
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.studentNumber = :studentNumber and s.active = true")
    Optional<Student> findActiveByStudentNumberForUpdate(@Param("studentNumber") String studentNumber);
}
