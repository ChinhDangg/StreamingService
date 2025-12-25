-- Create namespace
CREATE SCHEMA IF NOT EXISTS media;

CREATE SCHEMA IF NOT EXISTS auth;


-----------------------------------------
-- media_upload_service
-----------------------------------------
DO $$
    BEGIN
        CREATE USER media_upload_service WITH PASSWORD 'superSecretValue';
    EXCEPTION
        WHEN duplicate_object THEN
            -- user already exists, do nothing
            NULL;
    END
$$;

GRANT USAGE ON SCHEMA media TO media_upload_service;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA media
    TO media_upload_service;

ALTER DEFAULT PRIVILEGES IN SCHEMA media
    GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLES TO media_upload_service;


-----------------------------------------
-- search_indexer
-----------------------------------------
DO $$
    BEGIN
        CREATE USER search_indexer WITH PASSWORD 'superSecretValue';
    EXCEPTION
        WHEN duplicate_object THEN
            NULL;
    END
$$;

GRANT USAGE ON SCHEMA media TO search_indexer;

GRANT SELECT
    ON ALL TABLES IN SCHEMA media
    TO search_indexer;

ALTER DEFAULT PRIVILEGES IN SCHEMA media
    GRANT SELECT
    ON TABLES TO search_indexer;


-----------------------------------------
-- auth_service
-----------------------------------------
DO $$
    BEGIN
        CREATE USER auth_service WITH PASSWORD 'superSecretValue';
    EXCEPTION
        WHEN duplicate_object THEN
            NULL;
    END
$$;

GRANT CREATE ON SCHEMA auth TO auth_service;

GRANT USAGE ON SCHEMA auth TO auth_service;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA auth TO auth_service;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE auth.users
    TO auth_service;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLES TO auth_service;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT USAGE, SELECT ON SEQUENCES TO auth_service;