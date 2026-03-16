#!/bin/bash
set -e

echo "Waiting for PostgreSQL to be fully ready..."

until PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d "$DB_NAME" \
  -c "SELECT 1;" >/dev/null 2>&1; do
    echo "Postgres not ready yet..."
    sleep 2
done

echo "PostgreSQL is READY. Running bootstrap SQL..."

PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d "$DB_NAME" \
  -f /scripts/00-access.sql

echo "Bootstrap SQL complete."
