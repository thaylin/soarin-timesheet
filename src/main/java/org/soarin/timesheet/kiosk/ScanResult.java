package org.soarin.timesheet.kiosk;

import java.time.Duration;

/**
 * What {@link KioskService#scan(String)} returns: everything the kiosk is allowed to show
 * after a scan, and nothing more.
 *
 * <p><b>FERPA:</b> this record is intentionally tiny. It carries the first name + last initial
 * and today's hours - never the full name, the student ID, or any other record. If you are
 * tempted to add a field here, remember the kiosk screen is visible to everyone in the shop.
 *
 * <p>(A Java {@code record} is a compact, immutable class: the compiler generates the
 * constructor, the accessor methods such as {@code outcome()}, and {@code equals/hashCode}.)
 *
 * @param outcome     what happened (see {@link ScanOutcome})
 * @param displayName e.g. "Ada L."; {@code null} when the outcome is {@link ScanOutcome#UNKNOWN_ID}
 * @param todayTotal  time in the shop during the current shop day (midnight to midnight,
 *                    America/New_York), including an entry that is still open;
 *                    {@code null} when the outcome is {@link ScanOutcome#UNKNOWN_ID}
 */
public record ScanResult(ScanOutcome outcome, String displayName, Duration todayTotal) {

    /** The result for an unrecognised ID. Contains no student information at all. */
    public static ScanResult unknown() {
        return new ScanResult(ScanOutcome.UNKNOWN_ID, null, null);
    }
}
