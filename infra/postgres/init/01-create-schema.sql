-- Runs once, when the Postgres data volume is first created.
-- The database itself is created by the POSTGRES_DB environment variable.
-- All Bayflow tables live in schema "bayflow"; each service owns its own
-- table prefix (auth_, apt_, ws_) and its own Flyway history table.

CREATE SCHEMA IF NOT EXISTS bayflow AUTHORIZATION CURRENT_USER;

DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET search_path TO bayflow, public', current_database());
END
$$;
