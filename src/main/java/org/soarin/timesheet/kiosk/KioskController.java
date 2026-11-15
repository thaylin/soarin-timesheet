package org.soarin.timesheet.kiosk;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP API used by the kiosk page ({@code static/kiosk.js}).
 *
 * <h2>The whole API: one endpoint</h2>
 * <pre>
 * POST /api/kiosk/scan
 * Content-Type: application/json
 *
 * {"studentId": "123456"}
 * </pre>
 * Response (always JSON; see {@link ScanResponse}):
 * <pre>
 * {"status":"SIGNED_IN","displayName":"Ada L.","todayHours":"1h 05m","todayMinutes":65,"message":"Signed in"}
 * </pre>
 *
 * <h2>FERPA / privacy design - please keep these properties when changing this class</h2>
 * <ul>
 *   <li><b>No way to list or look up students.</b> There is deliberately no GET endpoint. The
 *       only thing you can do is "scan", which only reveals first name + last initial of the
 *       student whose badge you hold.</li>
 *   <li><b>No PII in URLs.</b> The student ID travels in the POST body, not the URL, so it never
 *       ends up in browser history, proxy logs or access logs.</li>
 *   <li><b>JSON only.</b> {@code consumes = application/json} means a random web page can't
 *       submit a plain HTML form at the kiosk API (browsers won't send cross-site JSON without
 *       permission), which blocks cross-site request forgery from other sites.</li>
 *   <li><b>Nothing echoed back, nothing logged.</b> Error responses never repeat the input.</li>
 * </ul>
 *
 * <p>Admin features (reports, corrections, roster management) belong in a separate,
 * password-protected controller under e.g. {@code /admin} - not here.
 *
 * <p>({@code @RestController} tells Spring that this class handles web requests and that return
 * values are converted to JSON automatically.)
 */
@RestController
@RequestMapping("/api/kiosk")
public class KioskController {

    private static final Logger log = LoggerFactory.getLogger(KioskController.class);

    private final KioskService kioskService;

    public KioskController(KioskService kioskService) {
        this.kioskService = kioskService;
    }

    /**
     * The JSON body the kiosk sends.
     *
     * @param studentId exactly what was scanned or typed
     */
    public record ScanRequest(String studentId) {
    }

    /**
     * The JSON the kiosk receives back.
     *
     * @param status       one of the {@link ScanOutcome} names, or {@code "ERROR"}
     * @param displayName  first name + last initial, or {@code null}
     * @param todayHours   today's total, pre-formatted for display (e.g. {@code "2h 05m"}), or {@code null}
     * @param todayMinutes today's total in whole minutes, or {@code null}
     * @param message      a short human-friendly sentence to show in big letters
     */
    public record ScanResponse(String status, String displayName, String todayHours, Long todayMinutes,
                               String message) {
    }

    /** Records a scan and tells the kiosk what to display. */
    @PostMapping(path = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ScanResponse scan(@RequestBody ScanRequest request) {
        ScanResult result = kioskService.scan(request.studentId());
        Duration today = result.todayTotal();
        return new ScanResponse(
                result.outcome().name(),
                result.displayName(),
                today == null ? null : formatHours(today),
                today == null ? null : today.toMinutes(),
                messageFor(result.outcome()));
    }

    /** The sentence shown to the student for each outcome. Keep these friendly and short. */
    static String messageFor(ScanOutcome outcome) {
        return switch (outcome) {
            case SIGNED_IN -> "Signed in. Have a great session!";
            case SIGNED_OUT -> "Signed out. See you next time!";
            case ALREADY_SIGNED_IN -> "You're already signed in - no need to scan again.";
            case ALREADY_SIGNED_OUT -> "You're already signed out - no need to scan again.";
            case UNKNOWN_ID -> "ID not recognized. Nothing was recorded - please see a mentor.";
        };
    }

    /** Formats a duration as hours and zero-padded minutes, e.g. 125 minutes -> "2h 05m". */
    static String formatHours(Duration duration) {
        long minutes = duration.toMinutes();
        return String.format("%dh %02dm", minutes / 60, minutes % 60);
    }

    // ---------------------------------------------------------------------------------------
    // Error handling. Methods marked @ExceptionHandler run instead of the normal response when
    // the named exception is thrown while handling a request in this controller. Handling them
    // here (rather than letting Spring's defaults log them) keeps request contents out of the logs.
    // ---------------------------------------------------------------------------------------

    /**
     * Two things tried to change the same time entry at the same moment (for example a scan at
     * exactly 00:00:05 while the midnight job runs). Nothing was saved; scanning again will work.
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ScanResponse> conflict(RuntimeException e) {
        log.warn("Kiosk scan conflicted with a concurrent update ({}); asked the student to retry",
                e.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, "Please scan again.");
    }

    /** The request body was missing or wasn't valid JSON. We don't log the body on purpose. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ScanResponse> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Please scan again.");
    }

    private static ResponseEntity<ScanResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ScanResponse("ERROR", null, null, null, message));
    }
}
