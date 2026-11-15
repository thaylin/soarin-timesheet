package org.soarin.timesheet.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * A student on the team roster. This is an education record under FERPA: keep the set of
 * fields minimal and never expose more than {@link #kioskDisplayName()} on the kiosk.
 *
 * <p>Students are deactivated rather than deleted so their time history is preserved.
 *
 * <p>Maps to the {@code student} table created in {@code db/migration/V1__init.sql}. If you add
 * a field here you must also add the column in a <em>new</em> migration file (the app refuses to
 * start if the entity and the table don't match). Ideas already planned for later: a profile
 * picture reference (admin-only), group/subteam membership. Before adding any field, ask
 * whether the app truly needs it - every extra field is more education-record data to protect.
 */
@Entity
@Table(name = "student")
public class Student {

    /** Longest student ID the kiosk accepts (matches the column size). */
    public static final int MAX_STUDENT_NUMBER_LENGTH = 32;

    /**
     * Internal database key, assigned by PostgreSQL ({@code IDENTITY}). Not the school ID, and
     * never shown to anyone - use it to link tables together.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The school-issued ID printed on the badge barcode. Stored normalized (see {@link #normalizeStudentNumber}). */
    @Column(name = "student_number", nullable = false, unique = true, length = MAX_STUDENT_NUMBER_LENGTH)
    private String studentNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    /** Inactive students can't sign in (the kiosk treats them as unknown) but keep their history. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Student() {
        // for JPA
    }

    public Student(String studentNumber, String firstName, String lastName) {
        this.studentNumber = normalizeStudentNumber(studentNumber);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * Canonical form of a scanned or typed ID: surrounding whitespace removed, upper-cased so a
     * scanner with caps lock on (or a hand-typed lowercase letter) still matches.
     */
    public static String normalizeStudentNumber(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * First name and last initial only, e.g. "Ada L." - the most the kiosk may show (FERPA
     * data minimisation). Uses code points so a name starting with an emoji or an accented
     * letter isn't cut in half.
     */
    public String kioskDisplayName() {
        String first = firstName == null ? "" : firstName.strip();
        String last = lastName == null ? "" : lastName.strip();
        if (last.isEmpty()) {
            return first;
        }
        return first + " " + last.substring(0, last.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT) + ".";
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public void setGraduationYear(Integer graduationYear) {
        this.graduationYear = graduationYear;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Deliberately omits names and IDs so a stray log statement cannot leak them. */
    @Override
    public String toString() {
        return "Student[id=" + id + "]";
    }
}
