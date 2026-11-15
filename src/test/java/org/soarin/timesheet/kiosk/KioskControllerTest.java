package org.soarin.timesheet.kiosk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soarin.timesheet.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the kiosk HTTP API the way the browser uses it ({@link MockMvc} sends fake HTTP requests
 * through the real Spring web layer without opening a network port). Also checks the privacy
 * properties promised in {@link KioskController}'s documentation.
 */
class KioskControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void addStudent() {
        createStudent("123456", "Ada", "Lovelace");
    }

    private static String body(String studentId) {
        return "{\"studentId\":\"" + studentId + "\"}";
    }

    @Test
    void scanReturnsMinimalDisplayInformation() throws Exception {
        String json = mvc.perform(post("/api/kiosk/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123456")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.displayName").value("Ada L."))
                .andExpect(jsonPath("$.todayHours").value("0h 00m"))
                .andExpect(jsonPath("$.todayMinutes").value(0))
                .andExpect(jsonPath("$.message").value(containsString("Signed in")))
                .andReturn().getResponse().getContentAsString();

        // FERPA: neither the full last name nor the student ID may come back.
        assertThat(json).doesNotContain("Lovelace", "123456");
    }

    @Test
    void unknownIdReturnsNoStudentData() throws Exception {
        mvc.perform(post("/api/kiosk/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN_ID"))
                .andExpect(jsonPath("$.displayName").value(nullValue()))
                .andExpect(jsonPath("$.todayHours").value(nullValue()));
        assertThat(entries.count()).isZero();
    }

    @Test
    void duplicateScanIsReportedAsAlreadyRecorded() throws Exception {
        mvc.perform(post("/api/kiosk/scan").contentType(MediaType.APPLICATION_JSON).content(body("123456")));
        clock.advance(Duration.ofSeconds(3));

        mvc.perform(post("/api/kiosk/scan").contentType(MediaType.APPLICATION_JSON).content(body("123456")))
                .andExpect(jsonPath("$.status").value("ALREADY_SIGNED_IN"))
                .andExpect(jsonPath("$.message").value(containsString("already signed in")));
    }

    @Test
    void formPostsAreRejectedSoOtherSitesCannotSubmitScans() throws Exception {
        mvc.perform(post("/api/kiosk/scan")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("studentId=123456"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(entries.count()).isZero();
    }

    @Test
    void malformedJsonGetsAFriendlyError() throws Exception {
        mvc.perform(post("/api/kiosk/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void thereIsNoWayToReadStudentsThroughTheKioskApi() throws Exception {
        mvc.perform(get("/api/kiosk/scan")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/kiosk/students")).andExpect(status().isNotFound());
        mvc.perform(get("/api/kiosk/scan/123456")).andExpect(status().isNotFound());
    }

    @Test
    void kioskPageIsServedWithSecurityHeaders() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void hoursAreFormattedAsHoursAndPaddedMinutes() {
        assertThat(KioskController.formatHours(Duration.ofMinutes(125))).isEqualTo("2h 05m");
        assertThat(KioskController.formatHours(Duration.ofMinutes(59))).isEqualTo("0h 59m");
        assertThat(KioskController.formatHours(Duration.ofHours(10))).isEqualTo("10h 00m");
    }
}
