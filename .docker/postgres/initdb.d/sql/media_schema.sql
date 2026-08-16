-- 1. Create the user
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'media_handler') THEN
    CREATE ROLE media_handler WITH LOGIN PASSWORD 'superSecretValue';
  END IF;
END
$$;

-- 2. Hand over ownership of the database
ALTER DATABASE media_db OWNER TO media_handler