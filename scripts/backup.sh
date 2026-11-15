#!/usr/bin/env bash
# =====================================================================================
# backup.sh - make a backup of the timesheet database.
#
# Usage (from anywhere; the app must be running with `docker compose up -d`):
#     scripts/backup.sh
#
# What it does:
#   1. Runs pg_dump INSIDE the database container (so you don't need PostgreSQL tools
#      installed on the Pi) and saves the output to backups/soarin-YYYYMMDD-HHMMSS.dump
#      in "custom" format (compressed; restore with scripts/restore.sh).
#   2. Checks the file is a readable backup (pg_restore --list).
#   3. Deletes old backups, keeping the newest $KEEP (default 30).
#
# Settings (optional environment variables):
#     BACKUP_DIR   where to put backups            (default: <repo>/backups)
#     KEEP         how many backups to keep        (default: 30)
#
# Run it nightly with cron - see "Backups" in README.md.
#
# FERPA: backups contain student education records. They are created readable only by
# the current user (umask 077). Store copies somewhere physically secure (e.g. an
# encrypted USB drive kept by a mentor), never in a public or personal cloud folder.
# =====================================================================================
set -euo pipefail

# Always run from the repository root so `docker compose` finds docker-compose.yml.
cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP="${KEEP:-30}"

umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
file="$BACKUP_DIR/soarin-$stamp.dump"
partial="$file.partial"

echo "Backing up database to $file ..."
# Single quotes: $POSTGRES_USER / $POSTGRES_DB are expanded inside the container, where
# docker-compose.yml has already set them.
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner' > "$partial"
# Only give the file its final name once pg_dump finished successfully.
mv "$partial" "$file"

# Sanity check: can pg_restore read it?
docker compose exec -T db pg_restore --list < "$file" > /dev/null
echo "Backup OK ($(du -h "$file" | cut -f1))."

# Keep only the newest $KEEP backups.
old_backups="$(ls -1t "$BACKUP_DIR"/soarin-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))")"
if [ -n "$old_backups" ]; then
    echo "Removing old backups beyond the newest $KEEP:"
    echo "$old_backups"
    echo "$old_backups" | xargs rm -f --
fi
