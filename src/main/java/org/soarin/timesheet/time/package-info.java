/**
 * Time-zone and shop-day helpers.
 *
 * <p>The golden rules for time in this codebase:
 * <ol>
 *   <li>Store and pass around {@link java.time.Instant} (an exact moment, effectively UTC).</li>
 *   <li>Get "now" from the injected {@link java.time.Clock}, never {@code Instant.now()}.</li>
 *   <li>Whenever you need a <em>local day</em> (today's hours, midnight, reports by date), go
 *       through {@link org.soarin.timesheet.time.ShopCalendar}. It knows the shop's time zone and
 *       handles daylight saving time - days can be 23 or 25 hours long.</li>
 * </ol>
 */
package org.soarin.timesheet.time;
