#!/usr/bin/env bash
# =====================================================================================
# import-students.sh - add or update students from a CSV file.
#
# This is a stop-gap until the admin area ("Add, Remove and Edit Students") exists.
#
# Usage:
#     scripts/import-students.sh roster.csv
#
# CSV format - a header row and exactly these four columns (graduation_year may be empty):
#
#     student_number,first_name,last_name,graduation_year
#     123456,Ada,Lovelace,2027
#     A-778,Grace,Hopper,
#
# Rules:
#   * student_number is trimmed and upper-cased, and may only contain letters, digits
#     and dashes (max 32). A bad row stops the whole import - nothing is half-imported.
#   * If a student_number already exists, that student's name and graduation year are
#     updated and the student is (re)activated. Nobody is ever deleted.
#
# FERPA: the CSV is a student record. Delete it after importing, and don't email it
# around. (*.csv files are git-ignored so it can't be committed by accident.)
# =====================================================================================
set -euo pipefail

if [ $# -ne 1 ] || [ ! -f "$1" ]; then
    echo "Usage: $0 <students.csv>" >&2
    exit 2
fi
csv="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"

cd "$(dirname "$0")/.."

container_csv=/tmp/students-import.csv

# Copy the CSV into the database container, and make sure it's removed again afterwards
# even if the import fails.
docker compose cp "$csv" "db:$container_csv"
cleanup() { docker compose exec -T db rm -f "$container_csv" || true; }
trap cleanup EXIT

# The SQL below runs in one transaction: load the CSV into a temporary table, then
# insert-or-update the real student table from it.
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
BEGIN;

CREATE TEMP TABLE student_import (
    student_number  text,
    first_name      text,
    last_name       text,
    graduation_year text
) ON COMMIT DROP;

\copy student_import FROM '$container_csv' WITH (FORMAT csv, HEADER true)

INSERT INTO student (student_number, first_name, last_name, graduation_year)
SELECT upper(btrim(student_number)),
       btrim(first_name),
       btrim(last_name),
       NULLIF(btrim(graduation_year), '')::integer
FROM student_import
WHERE btrim(coalesce(student_number, '')) <> ''
ON CONFLICT (student_number) DO UPDATE
    SET first_name      = EXCLUDED.first_name,
        last_name       = EXCLUDED.last_name,
        graduation_year = EXCLUDED.graduation_year,
        active          = TRUE,
        updated_at      = now();

SELECT count(*) AS students_in_file FROM student_import;

COMMIT;
SQL

echo "Import finished."
