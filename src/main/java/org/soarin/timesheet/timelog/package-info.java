/**
 * The time log: {@link org.soarin.timesheet.timelog.TimeEntry} rows (one per stretch of shop
 * time), their repository, and the end-of-day auto sign-out
 * ({@link org.soarin.timesheet.timelog.AutoCloseService} and its scheduler).
 *
 * <p>Future features that read this data - reports, required-hours / remaining-hours
 * calculations, manual corrections - should live in new classes (or a new package such as
 * {@code org.soarin.timesheet.reports}) that use {@link org.soarin.timesheet.timelog.TimeEntryRepository}
 * and {@link org.soarin.timesheet.time.ShopCalendar}, so day boundaries are always computed the
 * same way.
 */
package org.soarin.timesheet.timelog;
