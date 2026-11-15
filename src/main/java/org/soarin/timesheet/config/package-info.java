/**
 * Application-wide wiring: settings ({@link org.soarin.timesheet.config.ShopProperties}),
 * the clock and calendar beans, background scheduling, and HTTP security headers.
 *
 * <p>When the admin area is added, its Spring Security configuration (login, password hashing,
 * which URLs need a login) belongs here too. Keep {@code /} and {@code /api/kiosk/**} public and
 * require a login for everything under {@code /admin}.
 */
package org.soarin.timesheet.config;
