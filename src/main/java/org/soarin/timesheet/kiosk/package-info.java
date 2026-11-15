/**
 * The sign-in kiosk: the only part of the app that is open to anyone on the shop network.
 *
 * <ul>
 *   <li>{@link org.soarin.timesheet.kiosk.KioskController} - the single HTTP endpoint
 *       ({@code POST /api/kiosk/scan}).</li>
 *   <li>{@link org.soarin.timesheet.kiosk.KioskService} - the sign-in/sign-out rules.</li>
 *   <li>{@link org.soarin.timesheet.kiosk.ScanResult} / {@link org.soarin.timesheet.kiosk.ScanOutcome}
 *       - what the kiosk may show.</li>
 *   <li>The page itself is plain HTML/CSS/JS in {@code src/main/resources/static/}.</li>
 * </ul>
 *
 * <p>Because this package is unauthenticated, it must never gain endpoints that list, search or
 * display student records. Put admin features in their own package behind a login.
 */
package org.soarin.timesheet.kiosk;
