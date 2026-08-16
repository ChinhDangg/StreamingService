-- 1. Create the user
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'auth_service') THEN
    CREATE ROLE auth_service WITH LOGIN PASSWORD 'superSecretValue';
  END IF;
END
$$;

-- 2. Hand over ownership of the database
ALTER DATABASE auth_db OWNER TO auth_service