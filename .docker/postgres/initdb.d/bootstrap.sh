#!/bin/bash
set -e

echo "Waiting for PostgreSQL to be fully ready..."

until PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d postgres \
  -c "SELECT 1;" >/dev/null 2>&1; do
    echo "Postgres not ready yet..."
    sleep 2
done

echo "PostgreSQL is READY. Running bootstrap SQL..."

#PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
#  -h "$DB_HOST" \
#  -p "$DB_PORT" \
#  -U "$DB_SUPERUSER" \
#  -d "$DB_NAME" \
#  -f /scripts/00-access.sql


# Step 1: Connect to default 'postgres' DB and create the new database
PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d postgres \
  <<-EOSQL
    SELECT 'CREATE DATABASE auth_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
EOSQL

# STEP 2: Connect directly to new database and run the SQL file
PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -v auth_pass="$DB_AUTH_SERVICE_PASSWORD" \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d auth_db \
  -f /scripts/sql/auth_schema.sql


PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d postgres \
  <<-EOSQL
    SELECT 'CREATE DATABASE media_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'media_db')\gexec
EOSQL

PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -v media_pass="$DB_MEDIA_HANDLER_PASSWORD" \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d media_db \
  -f /scripts/sql/media_schema.sql


PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d postgres \
  <<-EOSQL
    SELECT 'CREATE DATABASE search_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'search_db')\gexec
EOSQL

PGPASSWORD="$DB_SUPERUSER_PASSWORD" psql \
  -v ON_ERROR_STOP=1 \
  -v search_pass="$DB_SEARCH_SERVICE_PASSWORD" \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_SUPERUSER" \
  -d media_db \
  -f /scripts/sql/search_schema.sql

echo "Bootstrap SQL complete."
