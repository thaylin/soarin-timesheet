package org.soarin.timesheet.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the small bits of logic on {@link Student}. */
class StudentTest {

    @Test
    void kioskDisplayNameIsFirstNameAndLastInitialOnly() {
        assertThat(new Student("1", "Ada", "Lovelace").kioskDisplayName()).isEqualTo("Ada L.");
    }

    @Test
    void kioskDisplayNameUpperCasesTheInitialAndTrims() {
        assertThat(new Student("1", " ada ", " lovelace").kioskDisplayName()).isEqualTo("ada L.");
    }

    @Test
    void kioskDisplayNameWithoutLastNameIsJustFirstName() {
        assertThat(new Student("1", "Ada", "").kioskDisplayName()).isEqualTo("Ada");
    }

    @Test
    void studentNumberIsTrimmedAndUpperCased() {
        assertThat(Student.normalizeStudentNumber("  ab-123\n")).isEqualTo("AB-123");
        assertThat(new Student(" ab-123 ", "A", "B").getStudentNumber()).isEqualTo("AB-123");
    }

    @Test
    void nullStudentNumberNormalizesToEmpty() {
        assertThat(Student.normalizeStudentNumber(null)).isEmpty();
    }

    @Test
    void toStringDoesNotLeakPersonalInformation() {
        String text = new Student("123456", "Ada", "Lovelace").toString();
        assertThat(text).doesNotContain("123456", "Ada", "Lovelace");
    }
}
