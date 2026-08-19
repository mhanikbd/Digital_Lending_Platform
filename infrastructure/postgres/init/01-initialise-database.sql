-- Cluster-level settings for the lending database.
--
-- Runs in both supported development setups:
--   Docker  - picked up automatically from /docker-entrypoint-initdb.d on first
--             initialisation of an empty data directory
--   Native  - applied by infrastructure/scripts/win-dev-setup.ps1
--
-- Only database-level concerns belong here. Everything inside the database -
-- schemas, tables, indexes, constraints - is owned by Flyway migrations in
-- backend/digital-lending-api/src/main/resources/db/migration.
--
-- The statements are wrapped in dynamic SQL because ALTER DATABASE requires a
-- literal name, and the database is called something different depending on how
-- it was created. current_database() keeps this file identical in both paths.

DO $$
DECLARE
    db text := current_database();
BEGIN
    -- All timestamps are stored and compared in UTC. Local time is a
    -- presentation concern, resolved in the client.
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', db, 'UTC');

    -- Reject accidental cross-schema object creation in the default search path.
    EXECUTE format('ALTER DATABASE %I SET search_path TO %L', db, 'public');

    -- Fail a statement that waits too long rather than holding a lock during a
    -- migration, or letting a runaway query sit through a business day.
    EXECUTE format('ALTER DATABASE %I SET lock_timeout TO %L', db, '10s');
    EXECUTE format('ALTER DATABASE %I SET idle_in_transaction_session_timeout TO %L', db, '60s');

    EXECUTE format('COMMENT ON DATABASE %I IS %L', db,
        'Digital Lending Platform - authoritative store for customer, application, loan, workflow and audit data');

    RAISE NOTICE 'Database % initialised. Schema objects are applied by Flyway on backend startup.', db;
END
$$;
