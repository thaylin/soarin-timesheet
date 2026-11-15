package org.soarin.timesheet.timelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import org.soarin.timesheet.student.Student;

/**
 * One stretch of shop time for a student: opened by a sign-in scan, closed by a sign-out scan
 * (or automatically at the end of the shop day). Timestamps are stored as UTC instants
 * ({@code timestamptz}); the shop-local day is derived via {@link org.soarin.timesheet.time.ShopCalendar}.
 *
 * <p>This is a time log, not an authentication session.
 *
 * <h2>JPA notes for maintainers</h2>
 * <ul>
 *   <li>{@code @Entity} + {@code @Table} map this class to the {@code time_entry} table (created
 *       by {@code db/migration/V1__init.sql}). Each {@code @Column} maps a field to a column.</li>
 *   <li>Fields are changed only through methods with business meaning ({@link #closeByScan},
 *       {@link #autoClose}) rather than plain setters, so the rules (e.g. "can't close twice",
 *       "sign-out can't be before sign-in") live in one place.</li>
 *   <li>The protected no-argument constructor exists only because JPA needs it to build objects
 *       from database rows. Application code uses the public constructor.</li>
 * </ul>
 *
 * <h2>Adding admin corrections later</h2>
 * Add a method such as {@code correct(Instant in, Instant out, String reason)} that sets
 * {@link CloseReason#MANUAL}, clears {@code needsReview}, and validates the times. Consider an
 * audit table (who changed what, when) in a new migration.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The student this time belongs to. Many entries belong to one student ({@code @ManyToOne}).
     * {@code LAZY} means the student row is only loaded from the database if something actually
     * uses this field.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @Column(name = "signed_in_at", nullable = false)
    private Instant signedInAt;

    @Column(name = "signed_out_at")
    private Instant signedOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 20)
    private CloseReason closeReason;

    /** Set when the entry needs an admin to look at it (e.g. it was auto-closed at midnight). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    /**
     * Optimistic lock: guards against a scan and the auto-close job closing the same entry at once.
     *
     * <p>Hibernate adds 1 to this number on every update and includes
     * {@code WHERE version = <old value>} in the UPDATE. If someone else changed the row in the
     * meantime, no row matches and Hibernate throws an optimistic-locking exception instead of
     * silently overwriting their change. Never set this field yourself.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeEntry() {
        // for JPA
    }

    public TimeEntry(Student student, Instant signedInAt) {
        this.student = Objects.requireNonNull(student, "student");
        this.signedInAt = Objects.requireNonNull(signedInAt, "signedInAt");
    }

    public boolean isOpen() {
        return signedOutAt == null;
    }

    /** Closes this entry because the student scanned out. */
    public void closeByScan(Instant at) {
        close(at, CloseReason.SCAN);
    }

    /** Closes this entry at the end of its shop day and flags it for admin review. */
    public void autoClose(Instant endOfShopDay) {
        close(endOfShopDay, CloseReason.AUTO_MIDNIGHT);
        this.needsReview = true;
    }

    private void close(Instant at, CloseReason reason) {
        if (!isOpen()) {
            throw new IllegalStateException("Time entry " + id + " is already closed");
        }
        if (at.isBefore(signedInAt)) {
            throw new IllegalArgumentException("Sign-out cannot be before sign-in for entry " + id);
        }
        this.signedOutAt = at;
        this.closeReason = reason;
    }

    /**
     * When the student last physically scanned for this entry: the sign-out scan if there was
     * one, otherwise the sign-in. Automatic closes are not scans, so they don't count.
     */
    public Instant lastScanAt() {
        return closeReason == CloseReason.SCAN ? signedOutAt : signedInAt;
    }

    /** JPA calls this automatically just before the row is first inserted. */
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /** JPA calls this automatically just before each update of the row. */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Instant getSignedInAt() {
        return signedInAt;
    }

    public Instant getSignedOutAt() {
        return signedOutAt;
    }

    public CloseReason getCloseReason() {
        return closeReason;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    @Override
    public String toString() {
        return "TimeEntry[id=" + id + "]";
    }
}
