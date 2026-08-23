-- 1. Create role if not exists using dynamic psql execution
SELECT format('CREATE ROLE auth_service WITH LOGIN PASSWORD %L', :'auth_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'auth_service')\gexec

-- 2. Ensure password is up to date if the role already existed
ALTER ROLE auth_service WITH PASSWORD :'auth_pass';

-- 3. Hand over ownership
ALTER DATABASE auth_db OWNER TO auth_service;