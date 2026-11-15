package org.soarin.timesheet.kiosk;

/**
 * Every possible result of scanning (or typing) a student ID at the kiosk.
 *
 * <p>The kiosk page picks its colour and headline from this value, so if you add a new outcome
 * here, also update:
 * <ul>
 *   <li>{@link KioskController#messageFor(ScanOutcome)} - the text shown to the student, and</li>
 *   <li>{@code src/main/resources/static/kiosk.js} / {@code kiosk.css} - how it is displayed.</li>
 * </ul>
 */
public enum ScanOutcome {

    /** A new time entry was opened - the student is now signed in. */
    SIGNED_IN,

    /** The student's open time entry was closed - the student is now signed out. */
    SIGNED_OUT,

    /**
     * The same student scanned again within the duplicate window (60 seconds by default) and
     * their last scan was a sign-in. Nothing was recorded. This stops a double "beep" from
     * signing someone in and immediately back out.
     */
    ALREADY_SIGNED_IN,

    /** Like {@link #ALREADY_SIGNED_IN}, but the last scan was a sign-out. Nothing was recorded. */
    ALREADY_SIGNED_OUT,

    /**
     * No active student has that ID, or the input didn't look like an ID at all. Nothing was
     * recorded. We intentionally don't say <em>which</em> of those it was.
     */
    UNKNOWN_ID
}
