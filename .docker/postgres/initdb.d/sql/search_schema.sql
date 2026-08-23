-- 1. Create the user
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'search_service') THEN
CREATE ROLE search_service WITH LOGIN PASSWORD 'superSecretValue';
END IF;
END
$$;

-- 2. Hand over ownership of the database
ALTER DATABASE search_db OWNER TO search_service