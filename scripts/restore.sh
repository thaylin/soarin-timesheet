#!/usr/bin/env bash
# =====================================================================================
# restore.sh - replace the timesheet database with a backup made by backup.sh.
#
# Usage:
#     scripts/restore.sh backups/soarin-20261115-020000.dump
#     scripts/restore.sh --yes backups/soarin-20261115-020000.dump   # skip the prompt
#
# !!! THIS OVERWRITES ALL CURRENT DATA with the contents of the backup. Anything recorded
# !!! since that backup was made is lost. Consider running scripts/backup.sh first.
#
# What it does:
#   1. Makes sure the database container is running.
#   2. Stops the app container so nobody scans while the restore runs.
#   3. Runs pg_restore --clean inside the database container, in a single transaction
#      (if anything goes wrong, the database is left exactly as it was).
#   4. Starts the app again. On start-up the app runs any newer Flyway migrations, so
#      restoring an older backup into a newer version of the app is fine.
# =====================================================================================
set -euo pipefail

assume_yes=false
if [ "${1:-}" = "--yes" ]; then
    assume_yes=true
    shift
fi

if [ $# -ne 1 ]; then
    echo "Usage: $0 [--yes] <backup-file.dump>" >&2
    exit 2
fi

# Resolve the backup path before changing directory.
backup="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
if [ ! -f "$backup" ]; then
    echo "Backup file not found: $1" >&2
    exit 1
fi

cd "$(dirname "$0")/.."

if [ "$assume_yes" != true ]; then
    echo "This will REPLACE ALL timesheet data with: $backup"
    read -r -p "Type 'restore' to continue: " answer
    if [ "$answer" != "restore" ]; then
        echo "Cancelled."
        exit 1
    fi
fi

echo "Starting database (if needed) ..."
docker compose up -d --wait db

echo "Stopping app ..."
docker compose stop app

echo "Restoring ..."
docker compose exec -T db sh -c \
    'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction' \
    < "$backup"

echo "Starting app ..."
docker compose start app
echo "Restore complete."
