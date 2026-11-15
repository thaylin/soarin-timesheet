# Architecture and maintainer guide

This document explains how the Soarin Timesheet code fits together, why it's built the way it
is, and how to add the planned features. It's written for team members who know some Java
but may be new to Spring Boot, JPA or Docker.

If you only read one section, read [The rules that keep this app correct](#the-rules-that-keep-this-app-correct).

---

## 1. The big picture

```
 ┌──────────────────────┐      HTTP (LAN only)      ┌─────────────────────────┐      JDBC      ┌──────────────┐
 │ Kiosk browser        │  POST /api/kiosk/scan     │ Spring Boot app (Java)  │  ───────────▶  │ PostgreSQL   │
 │ index.html/kiosk.js  │  ───────────────────────▶ │  container "app"        │                │ container    │
 │ + USB barcode scanner│  ◀─── JSON result ─────── │  port 8080              │                │ "db"         │
 └──────────────────────┘                           └─────────────────────────┘                │ volume pgdata│
                                                                                               └──────────────┘
```

- **Kiosk page** - plain HTML/CSS/JavaScript in `src/main/resources/static/`. No framework,
  nothing to build. The barcode scanner "types" into the page like a keyboard.
- **Spring Boot app** - one Java program that serves the page, handles scans, and runs the
  midnight auto sign-out job.
- **PostgreSQL** - stores students and time entries. Its schema is defined by SQL files in
  `src/main/resources/db/migration/`, applied automatically by Flyway at startup.
- **Docker Compose** - runs the two containers together on the Pi (`docker-compose.yml`).

### What happens when a student scans

1. The scanner types `123456⏎` into the focused input box.
2. `kiosk.js` catches the form submit, clears the box and sends
   `POST /api/kiosk/scan {"studentId":"123456"}`.
3. `KioskController.scan()` receives it and calls `KioskService.scan("123456")`.
4. `KioskService` (in one database transaction):
   1. normalises the ID (trim, upper-case) and rejects anything that doesn't look like an ID;
   2. looks up the active student, **locking** their row so a double scan waits its turn;
   3. auto-closes any entry left open from a previous day;
   4. ignores the scan if the student's last scan was < 60 s ago;
   5. closes their open entry (sign-out) **or** creates a new one (sign-in);
   6. adds up today's time.
5. The controller turns the result into JSON with a friendly message.
6. `kiosk.js` shows it in big coloured text and resets after 5 seconds.

---

## 2. Code tour

All Java code is under `src/main/java/org/soarin/timesheet/`. Every class has Javadoc
explaining what it does and why - read the code alongside this guide.

| Package | Classes | Responsibility |
|---|---|---|
| *(root)* | `TimesheetApplication` | `main()` - starts Spring Boot. |
| `config` | `ShopProperties`, `TimeConfig`, `SchedulingConfig`, `SecurityHeadersFilter` | Settings, the shared `Clock`, turning on scheduled jobs, HTTP security headers. |
| `time` | `ShopCalendar` | Converts instants ↔ New York shop days. **All** day/midnight maths goes through here. |
| `student` | `Student`, `StudentRepository` | The roster. |
| `timelog` | `TimeEntry`, `CloseReason`, `TimeEntryRepository`, `AutoCloseService`, `AutoCloseScheduler` | The time log and the midnight auto sign-out. |
| `kiosk` | `KioskController`, `KioskService`, `ScanResult`, `ScanOutcome` | The public kiosk: HTTP endpoint and sign-in/out rules. |
| `demo` | `DemoDataSeeder` | Fake students for the `demo` profile. |

Code is grouped **by feature** (student, timelog, kiosk) rather than by layer (all controllers
in one folder, all services in another). When you add a feature, add a package for it.

### The layers inside a feature

```
Controller   (HTTP in/out, JSON)          e.g. KioskController
   │ calls
Service      (business rules, @Transactional)   e.g. KioskService, AutoCloseService
   │ calls
Repository   (database queries)           e.g. StudentRepository, TimeEntryRepository
   │ loads/saves
Entity       (a Java object = a table row)      e.g. Student, TimeEntry
```

- **Controllers** stay thin: translate HTTP to a service call and back. No business rules.
- **Services** hold the rules and define transactions.
- **Repositories** are interfaces; Spring Data writes the implementation.
- **Entities** protect their own consistency (e.g. `TimeEntry.closeByScan` refuses to close
  twice or close before the sign-in time).

---

## 3. Data model

```
student                                   time_entry
───────────────────────────────           ─────────────────────────────────────────
id               bigint PK                id             bigint PK
student_number   varchar(32) UNIQUE  ◀──┐ student_id     bigint FK → student.id
first_name       varchar(100)           └ signed_in_at   timestamptz  NOT NULL
last_name        varchar(100)             signed_out_at  timestamptz  NULL = still signed in
graduation_year  integer NULL             close_reason   SCAN | AUTO_MIDNIGHT | MANUAL | NULL
active           boolean                  needs_review   boolean
created_at/updated_at timestamptz         version        integer (optimistic lock)
                                          created_at/updated_at timestamptz
```

- One **time entry** = one stretch of time in the shop. Sign-in creates it ("open",
  `signed_out_at` is NULL), sign-out fills in `signed_out_at`.
- This is a **time log**, not a login system. Students don't have accounts or sessions.
- The database **guarantees** a student never has two open entries
  (`uq_time_entry_one_open_per_student`, a partial unique index), and that sign-out is never
  before sign-in (`CHECK` constraints). Even buggy code can't store impossible data.
- Students are never deleted (`ON DELETE RESTRICT`); set `active = false` instead.

The full, commented schema is in
[`src/main/resources/db/migration/V1__init.sql`](../src/main/resources/db/migration/V1__init.sql).

---

## 4. The rules that keep this app correct

### Time

1. **Store instants.** Timestamps are `java.time.Instant` in Java and `timestamptz` in
   PostgreSQL - an exact moment, no time-zone ambiguity.
2. **Never call `Instant.now()` / `LocalDate.now()` in app code.** Inject `java.time.Clock`
   and call `clock.instant()`. Tests swap in a fake clock (`MutableClock`) to simulate
   midnight, DST changes, etc.
3. **Use `ShopCalendar` for anything about days.** "Today", "midnight", "hours on date X" -
   always via `ShopCalendar`. Don't do `instant.atZone(...)` yourself, and never assume a day
   is 24 hours: in New York it's 23 hours in March and 25 in November.
4. Day ranges are **half-open**: a day is `[midnight, next midnight)`. An entry auto-closed at
   exactly midnight belongs entirely to the previous day.

### Privacy (FERPA)

5. The kiosk shows only `Student.kioskDisplayName()` (first name + last initial), the
   outcome, and today's hours.
6. The kiosk API (`/api/kiosk/**`) never gets endpoints that list, search or return student
   records. Admin features go behind a login under `/admin`.
7. Never log student names or IDs. Log internal ids (`entry 42`) and counts. Don't add
   `toString()` methods that print personal data.
8. No personal data in URLs - use POST bodies.

See the [Privacy and FERPA section of the README](../README.md#privacy-and-ferpa) for the full list.

### Database

9. **Never edit a migration that has been released.** Add a new `V<next>__description.sql`.
10. Entities and migrations must match - Hibernate checks at startup
    (`ddl-auto: validate`) and refuses to start otherwise.

---

## 5. Spring Boot in five minutes

Spring Boot does a lot "by magic". Here's what the annotations you'll see actually mean:

| Annotation | Meaning |
|---|---|
| `@SpringBootApplication` | Start here; scan this package and below for components. |
| `@Component`, `@Service`, `@RestController`, `@Configuration` | "Spring, create one instance of this class at startup." These instances are called **beans**. |
| Constructor parameters | **Dependency injection**: Spring passes in the beans a class needs. We never write `new KioskService(...)` ourselves. |
| `@Bean` (in a `@Configuration`) | "Call this method once and make the result a bean" - used for objects we don't own, like `Clock`. |
| `@Transactional` | Everything in this method is one database transaction: all saved or none. Changes to loaded entities are saved automatically at the end. Only works on `public` methods called from **another** bean. |
| `@Entity`, `@Table`, `@Column`, `@Id` | JPA: map this class to a table and fields to columns. |
| `@ManyToOne` | A relationship: many time entries belong to one student. |
| `@Version` | Optimistic locking: Hibernate detects if someone else changed the row since we loaded it. |
| `@Lock(PESSIMISTIC_WRITE)` | Adds `FOR UPDATE` to a query: lock the rows until our transaction ends. |
| `@Query` | Hand-written query in JPQL (uses class/field names, not table/column names). |
| `@Scheduled(cron = ...)` | Run this method on a timer. |
| `@ConfigurationProperties` | Bind settings from `application.yml` to a Java object. |
| `@ConditionalOnProperty`, `@Profile` | Only create this bean when a setting/profile is on. |
| `@PostMapping`, `@RequestBody` | Handle `POST` requests; turn the JSON body into a Java object. |
| `@ExceptionHandler` | Turn a specific exception into a specific HTTP response. |

Good references: the [Spring Boot reference guide](https://docs.spring.io/spring-boot/reference/),
[Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/), and
[Flyway](https://documentation.red-gate.com/fd).

---

## 6. Testing

```bash
mvn verify      # needs Docker running (Testcontainers starts PostgreSQL)
```

| Test class | Kind | What it covers |
|---|---|---|
| `time/ShopCalendarTest` | Unit (fast, no Spring) | Day boundaries in EST/EDT, 23- and 25-hour DST days, splitting time across midnight. |
| `student/StudentTest` | Unit | Display name (first + last initial), ID normalisation, `toString` leaks nothing. |
| `kiosk/KioskServiceTest` | Integration (real DB) | Toggle, today's hours, 60 s duplicate suppression, unknown/inactive/junk IDs, New York vs UTC day, stale entries, DST. |
| `timelog/AutoCloseServiceTest` | Integration | Midnight auto-close, idempotency, Pi-off-for-days catch-up, all four DST midnights. |
| `kiosk/KioskControllerTest` | Integration (HTTP) | JSON shape, privacy (no full name/ID in responses, no read endpoints), JSON-only, security headers. |

Integration tests extend `support/IntegrationTest`, which starts the app once against a
throwaway PostgreSQL container, turns the scheduler off, empties the tables and resets the fake
clock before every test. To control time in a test:

```java
setNow("2026-11-14T23:59");             // New York local time
clock.advance(Duration.ofSeconds(30));  // move forward
```

**When you fix a bug, first write a test that fails because of it.** When you add a rule, add
tests for its edges (exactly 60 seconds, exactly midnight, DST days).

---

## 7. How to add the planned features

These are suggestions to keep the code consistent - adapt as needed.

### Adding a database column or table

1. Create `src/main/resources/db/migration/V2__add_student_group.sql` (next number, short
   description, two underscores).
2. Update/add the entity class to match.
3. Run `mvn verify` - Hibernate validation will tell you if they don't match.

### Admin area (password protected)

1. Add `spring-boot-starter-security` to `pom.xml`.
2. Create `config/SecurityConfig.java`: permit `/`, `/kiosk.*`, `/index.html` and
   `/api/kiosk/**` without login; require login for `/admin/**` and `/api/admin/**`; use form
   login and BCrypt password hashes. Keep CSRF protection on for the admin area.
3. Create an `admin` table (and a migration) for admin/mentor accounts - don't hard-code passwords.
4. Put admin controllers in their own package (e.g. `org.soarin.timesheet.admin`).
5. Consider server-rendered pages with `spring-boot-starter-thymeleaf` - no JavaScript build needed.

### Manual timesheet corrections

- Add a method on `TimeEntry` such as `correct(Instant in, Instant out)` that sets
  `CloseReason.MANUAL` and clears `needsReview`. `MANUAL` already exists in the enum and the
  database check constraint.
- The review queue is `SELECT ... WHERE needs_review` (there's already an index for it).
- Add an audit table recording who changed what and when.

### Mentors, administrators, groups, hour requirements, forms

- New entities + tables, each in its own package. Mentors are not students - give them their own table.
- Group membership: a `student_group` table and a `student_group_member` join table.
- Required hours per group: a column on the group table, plus a report service that sums
  time with `TimeEntryRepository.findOverlapping` and `ShopCalendar`.
- Forms (Safety Quiz, Consent and Release, Photo Release...): a `form_type` table and a
  `student_form` table with a completed flag/date.

### Reports

Create a `reports` package with services that read `TimeEntryRepository` and use
`ShopCalendar.overlapWithDay(...)` to split time by day. Never group by the UTC date in SQL -
convert with `AT TIME ZONE 'America/New_York'` or do it in Java via `ShopCalendar`.

### Remaining-hours display on sign-in (`+` in the feature list)

Add a field such as `remainingRequired` to `ScanResult` / `ScanResponse`, computed by a new
service, and show it in `kiosk.js`. It's an hours number, not personal detail, but double-check
it fits the kiosk minimisation rule before shipping.

### Profile pictures (`+`, admin only)

Store files on a Docker volume (not in git) and a path column on `student` (new migration).
Show them only in the admin area, not on the kiosk.
