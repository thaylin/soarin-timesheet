# Soarin Timesheet

A shop time log for the Soarin student robotics/shop team. Students scan their school ID
badge at a kiosk to sign in and out; the app records when they were in the shop.

It runs entirely on a Raspberry Pi on the shop's local network. There are **no cloud services,
no internet exposure and no external sign-in**.

- **Stack:** Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway migrations, Docker Compose
- **Runs on:** Raspberry Pi 4/5 with a 64-bit OS (ARM64) and any x86-64 machine
- **Feature list / roadmap:** [`key-features.md`](key-features.md)
- **New to the code?** Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) next.

---

## Contents

1. [What works today](#what-works-today)
2. [Running it](#running-it)
3. [Setting up the kiosk computer](#setting-up-the-kiosk-computer)
4. [Adding students](#adding-students)
5. [Backups and restore](#backups-and-restore)
6. [Keeping the Pi's clock right](#keeping-the-pis-clock-right)
7. [Privacy and FERPA](#privacy-and-ferpa)
8. [Configuration reference](#configuration-reference)
9. [Development](#development)
10. [Troubleshooting](#troubleshooting)

---

## What works today

This is the first "vertical slice" of the app: the kiosk, end to end.

| Feature | How it behaves |
|---|---|
| **Scan to sign in/out** | Scan a badge (or type the ID and press Enter). The first scan signs you in, the next scan signs you out, and so on. |
| **Big, clear confirmation** | The screen turns green (signed in), blue (signed out), amber (repeat scan) or red (problem) and shows the first name + last initial and today's hours. It resets for the next person after 5 seconds. |
| **Repeat-scan protection** | A second scan of the same badge within 60 seconds is ignored and shows "already signed in/out", so a double beep can't sign you straight back out. |
| **Unknown IDs** | Clear "ID not recognized" message. Nothing is recorded. |
| **Auto sign-out at midnight** | Anyone still signed in at midnight is signed out automatically at exactly midnight, and that entry is flagged `needs_review` so an admin can fix the real time later. |
| **Shop day = midnight to midnight, New York time** | All "today" calculations use America/New_York and handle EST/EDT changes. Timestamps are stored as exact UTC instants. |

**Not built yet** (planned, see [`key-features.md`](key-features.md)): the password-protected
admin area (students, mentors, admins, groups, hour requirements, forms), manual timesheet
corrections, and reports. Items marked `+` there (remaining-hours display, profile pictures)
are future ideas; the design leaves room for them.

---

## Running it

You need **Docker** with the **Compose plugin** on the Pi (or any computer).
On Raspberry Pi OS (64-bit):

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER    # then log out and back in
```

Then, in a copy of this repository:

```bash
cp .env.example .env
nano .env                         # set POSTGRES_PASSWORD to something long and random
docker compose up -d --build      # first build takes a few minutes on a Pi
```

Open **http://&lt;pi-address&gt;:8080/** in a browser. That's the kiosk.

Useful commands:

```bash
docker compose ps                 # is everything running?
docker compose logs -f app        # watch the app's log
docker compose restart app        # restart just the app
docker compose down               # stop everything (data is kept)
git pull && docker compose up -d --build   # update to the latest code
```

Data lives in the Docker volume `pgdata`, so it survives restarts, rebuilds and
`docker compose down`. **`docker compose down -v` deletes the volume and all data - don't.**

Both containers have `restart: unless-stopped`, so they come back automatically after the Pi
reboots.

### Trying it with fake students

Set `SPRING_PROFILES_ACTIVE=demo` in `.env` and run `docker compose up -d`. If the roster is
empty, three fake students are added with IDs `DEMO-1001`, `DEMO-1002` and `DEMO-1003`.
Don't use the demo profile on the real kiosk.

---

## Setting up the kiosk computer

The kiosk can be the Pi itself (with a monitor) or any other computer on the network.

1. **Barcode scanner:** use a USB scanner in its default "keyboard" (HID) mode, configured to
   send **Enter** after each scan (most do by default). The page keeps the input box focused,
   so scans always land in the right place.
2. **Browser in kiosk mode** (full screen, no address bar). On Raspberry Pi OS:
   ```bash
   chromium-browser --kiosk --incognito --noerrdialogs --disable-session-crashed-bubble http://localhost:8080/
   ```
   `--incognito` stops the browser keeping history or autofill entries (which would contain
   student IDs). To start it automatically at login, add that line to the desktop autostart
   (for example `~/.config/labwc/autostart` on Raspberry Pi OS Bookworm with Wayland).
3. Test: scan a badge, check the name and colour, scan again after a minute to sign out.

---

## Adding students

Until the admin area exists, load the roster from a CSV file:

```csv
student_number,first_name,last_name,graduation_year
123456,Ada,Lovelace,2027
A-778,Grace,Hopper,
```

```bash
scripts/import-students.sh roster.csv
rm roster.csv     # it's a student record - don't leave copies lying around
```

Re-running with an updated file updates names and re-activates students; nobody is deleted.
IDs are matched case-insensitively and surrounding spaces are ignored. See the comments at the
top of [`scripts/import-students.sh`](scripts/import-students.sh) for details.

To deactivate a student by hand (they can no longer sign in; their history is kept):

```bash
docker compose exec db psql -U soarin -d soarin \
  -c "UPDATE student SET active = false WHERE student_number = '123456';"
```

---

## Backups and restore

Backups use PostgreSQL's own `pg_dump`, run inside the database container.

### Make a backup

```bash
scripts/backup.sh
```

This writes `backups/soarin-YYYYMMDD-HHMMSS.dump`, checks it can be read back, and keeps the
newest 30 (change with `KEEP=60 scripts/backup.sh`, or the folder with `BACKUP_DIR=/mnt/usb`).

### Back up automatically every night

Run `crontab -e` on the Pi and add (adjust the path to where the repository is):

```cron
# 2:30 AM every night. Log goes to backups/backup.log.
30 2 * * * cd /home/pi/soarin-timesheet && scripts/backup.sh >> backups/backup.log 2>&1
```

**Copy backups off the Pi regularly** - an SD card can fail and take the backups with it.
A USB drive kept by a mentor works well, for example:

```bash
rsync -a backups/ /media/pi/SOARIN-BACKUP/soarin/
```

### Restore a backup

```bash
scripts/backup.sh                                     # optional: save the current state first
scripts/restore.sh backups/soarin-20261115-023000.dump
```

The restore asks you to type `restore` to confirm, stops the app, replaces **all** data with
the backup in a single transaction (if it fails, nothing changes), and starts the app again.

### Moving to a new Pi

1. On the old Pi: `scripts/backup.sh`, then copy the newest `.dump` file and your `.env`.
2. On the new Pi: clone the repository, put `.env` in place, run `docker compose up -d --build`.
3. `scripts/restore.sh --yes path/to/the.dump`.

### Testing your backups

A backup you've never restored is just a hope. Once a term, restore the latest backup on a
spare computer (steps above) and check that recent sign-ins are there.

---

## Keeping the Pi's clock right

**This matters more than it sounds.** Every sign-in time comes from the Pi's clock. A
Raspberry Pi has **no battery-backed clock**: after a power cut it guesses the time until it
can reach a time server. If the shop network has no internet access, it may never correct
itself, and every timestamp will be wrong.

Do at least one of:

- **Let the Pi reach a time server.** Raspberry Pi OS syncs automatically (`systemd-timesyncd`)
  if outbound NTP (UDP port 123) is allowed. Many school routers also act as a time server -
  set it in `/etc/systemd/timesyncd.conf` (`NTP=<router-ip>`).
- **Add a real-time clock.** The Raspberry Pi 5 has an RTC connector (needs a small battery);
  older Pis can use a cheap RTC HAT (e.g. DS3231).

Check the clock with `timedatectl` - it should say `System clock synchronized: yes`.
The time zone of the Pi itself doesn't matter: the app always uses America/New_York.

---

## Privacy and FERPA

Shop attendance tied to a student is part of the student's **education record** under FERPA
(the US Family Educational Rights and Privacy Act). These are the decisions this app makes to
protect it. **Please keep them when adding features.**

| Decision | Why |
|---|---|
| **Local network only.** No cloud services, analytics, CDNs, fonts or external scripts. The database port isn't published at all; only the app container can reach it. | Data never leaves the Pi. |
| **The kiosk shows the minimum:** first name + last initial, signed in/out, and today's hours. Never the full name, the student ID or anyone else's data. | The kiosk screen is visible to everyone in the shop. |
| **The kiosk can't list or look up students.** Its only API is `POST /api/kiosk/scan`. There's no endpoint to browse the roster or query a student. You only learn the first name + initial of a badge you physically hold. | An unauthenticated kiosk must not become a roster lookup tool. |
| **No personal information in URLs.** The ID is sent in the POST body. | URLs end up in browser history and logs. |
| **No names or IDs in application logs.** Logs only mention internal entry numbers and counts. `Student.toString()` deliberately prints only the internal id. SQL logging is off, and bad requests aren't echoed or logged. | Logs get copied around when debugging. |
| **The ID is cleared from the screen immediately** after Enter, and the page tells the browser not to autocomplete it. API responses are marked `Cache-Control: no-store`. Run the kiosk browser in incognito mode. | Next person in line shouldn't see the previous ID. |
| **Strict browser security headers** (Content-Security-Policy, no framing, no referrer). Kiosk API accepts JSON only. | Blocks cross-site scripting and stops other web pages from submitting fake scans. |
| **Minimal roster fields:** student number, first/last name, graduation year, active flag. | Less data = less to protect. Ask "do we really need it?" before adding a field. |
| **Deactivate, don't delete.** Inactive students can't sign in; their history is kept. | Records may need to be produced or corrected later. |
| **Backups are created owner-readable only** (`umask 077`) and are git-ignored, as are CSV imports. | Backups are full copies of the records. |

Still to do when the admin area is built (tracked as follow-up work):

- Password-protected admin login (hashed passwords, per-person accounts, no shared login).
- An audit trail of who changed which time entry.
- A retention policy: how long to keep records of students who have left, and a way to export
  a student's records if a parent/eligible student asks to inspect them.
- Consider HTTPS on the LAN (e.g. a reverse proxy with a local certificate) once admin
  passwords are sent over the network.

---

## Configuration reference

Set these in `.env` (used by `docker-compose.yml`):

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_PASSWORD` | *(required)* | Database password. Make it long and random. |
| `POSTGRES_DB` / `POSTGRES_USER` | `soarin` | Database name / user. |
| `APP_PORT` | `8080` | Port the kiosk is served on. |
| `SPRING_PROFILES_ACTIVE` | *(empty)* | `demo` adds fake students to an empty roster. |

App settings live in [`src/main/resources/application.yml`](src/main/resources/application.yml)
and can be overridden with environment variables (add them under `app: environment:` in
`docker-compose.yml`):

| Setting (env variable) | Default | Meaning |
|---|---|---|
| `soarin.zone` (`SOARIN_ZONE`) | `America/New_York` | Time zone that defines the shop day. |
| `soarin.duplicate-window` (`SOARIN_DUPLICATEWINDOW`) | `60s` | Repeat scans within this time are ignored. |
| `soarin.auto-close.cron` (`SOARIN_AUTOCLOSE_CRON`) | `5 0 * * * *` | When the auto sign-out job runs (every hour at :00:05; the midnight run does the real work). |

---

## Development

### Requirements

- **JDK 21** (e.g. [Eclipse Temurin](https://adoptium.net/))
- **Maven 3.9+**
- **Docker** running - the tests start a real PostgreSQL in a container using
  [Testcontainers](https://testcontainers.com/)

### Common tasks

```bash
mvn verify                         # compile and run all tests
mvn test -Dtest=KioskServiceTest   # run one test class
mvn spring-boot:run                # run the app against a local PostgreSQL on localhost:5432
docker compose up -d --build       # or just run the whole thing in Docker
```

### Continuous integration

Every push runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml): the full test suite,
and a Docker image build for both `linux/amd64` and `linux/arm64` (the Raspberry Pi) to prove
the image still builds for the Pi.

### Project layout

```
.
├── docker-compose.yml          app + PostgreSQL, persistent volume
├── Dockerfile                  multi-stage, multi-arch image build
├── pom.xml                     Maven build: dependencies and versions
├── scripts/                    backup.sh, restore.sh, import-students.sh
├── docs/ARCHITECTURE.md        how the code fits together; how to add features
└── src/
    ├── main/java/org/soarin/timesheet/
    │   ├── TimesheetApplication.java   entry point
    │   ├── config/     settings, clock, scheduling, security headers
    │   ├── time/       ShopCalendar: New York shop-day maths (DST-safe)
    │   ├── student/    Student entity + repository
    │   ├── timelog/    TimeEntry entity + repository, midnight auto sign-out
    │   ├── kiosk/      kiosk API + sign-in/out rules
    │   └── demo/       fake data for the "demo" profile
    ├── main/resources/
    │   ├── application.yml             configuration
    │   ├── db/migration/               Flyway SQL migrations (database schema)
    │   └── static/                     kiosk page: index.html, kiosk.js, kiosk.css
    └── test/java/...                   tests (same package layout)
```

---

## Troubleshooting

| Problem | What to check |
|---|---|
| `docker compose up` says `Set POSTGRES_PASSWORD in .env` | You haven't created `.env` - `cp .env.example .env` and edit it. |
| Kiosk page shows "Cannot reach the server" | `docker compose ps` - is `app` running? `docker compose logs app` for errors. |
| App keeps restarting, log mentions Flyway "checksum mismatch" | Someone edited an already-applied migration file. Undo that edit and put the change in a new `V<n>__...sql` file instead. |
| App log says "Schema-validation: missing column" | An entity was changed without a matching migration. Add the migration. |
| Every scan says "ID not recognized" | Is the student imported and active? Does the scanner send extra characters? Try typing the ID by hand. |
| Times are off by hours or days | The Pi's clock is wrong - see [Keeping the Pi's clock right](#keeping-the-pis-clock-right). |
| Someone forgot to sign out | Midnight auto sign-out closes the entry and flags it `needs_review`. Find them with `SELECT * FROM time_entry WHERE needs_review;` until the admin corrections screen exists. |
