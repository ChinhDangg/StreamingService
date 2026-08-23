-- 1. Create role if not exists using dynamic psql execution
SELECT format('CREATE ROLE media_handler WITH LOGIN PASSWORD %L', :'media_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'media_handler')\gexec

-- 2. Ensure password is up to date if the role already existed
ALTER ROLE media_handler WITH PASSWORD :'media_pass';


-- 2. Hand over ownership of the database
ALTER DATABASE media_db OWNER TO media_handler