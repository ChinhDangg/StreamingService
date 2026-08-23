-- 1. Create role if not exists using dynamic psql execution
SELECT format('CREATE ROLE search_service WITH LOGIN PASSWORD %L', :'search_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'search_service')\gexec

-- 2. Ensure password is up to date if the role already existed
ALTER ROLE search_service WITH PASSWORD :'search_pass';

-- 2. Hand over ownership of the database
ALTER DATABASE search_db OWNER TO search_service