package org.soarin.timesheet.timelog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link TimeEntry}. Like all Spring Data repositories this is just an
 * interface; Spring generates the implementation (see the notes on
 * {@link org.soarin.timesheet.student.StudentRepository}).
 *
 * <p>All {@link Instant} parameters are exact moments in time (UTC). Use
 * {@link org.soarin.timesheet.time.ShopCalendar} to turn a local shop day into the
 * {@code [from, to)} instants these queries expect - never build day boundaries by hand.
 */
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    /**
     * The student's most recent entry (by sign-in time), open or closed.
     *
     * <p>No {@code @Query} needed: Spring Data reads the method name.
     * "findFirst" = return at most one row, "ByStudentId" = {@code where student.id = ?},
     * "OrderBySignedInAtDesc" = newest first.
     */
    Optional<TimeEntry> findFirstByStudentIdOrderBySignedInAtDesc(Long studentId);

    /**
     * Entries for a student that overlap {@code [from, to)}; open entries count as ongoing.
     *
     * <p>An entry overlaps the range if it started before the range ends AND it ended after the
     * range starts (or hasn't ended). This catches entries that started yesterday and are
     * still running, as well as entries entirely inside the range.
     */
    @Query("""
            select e from TimeEntry e
            where e.student.id = :studentId
              and e.signedInAt < :to
              and (e.signedOutAt is null or e.signedOutAt > :from)
            """)
    List<TimeEntry> findOverlapping(@Param("studentId") Long studentId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    /** Entries still open that were started before {@code cutoff} (i.e. on an earlier shop day). */
    @Query("select e from TimeEntry e where e.signedOutAt is null and e.signedInAt < :cutoff")
    List<TimeEntry> findOpenStartedBefore(@Param("cutoff") Instant cutoff);
}
