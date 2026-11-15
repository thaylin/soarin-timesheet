package org.soarin.timesheet.timelog;

/** Why a time entry was closed. */
public enum CloseReason {
    /** The student scanned out at the kiosk. */
    SCAN,
    /** Still open at the end of the shop day; closed at local midnight and flagged for review. */
    AUTO_MIDNIGHT,
    /** Set or corrected by an admin (reserved for the admin corrections feature). */
    MANUAL
}
