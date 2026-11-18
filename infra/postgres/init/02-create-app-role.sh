#!/bin/sh
# Creates the login the Bayflow services use at runtime: bayflow_app. It is not a superuser,
# owns no tables and cannot bypass row-level security, so the per-workshop policies apply to
# it. Tables are owned by POSTGRES_USER, which only Flyway uses. Each service's V4 migration
# grants bayflow_app access to its own tables.
#
# Runs automatically on a new data volume. For an existing local database run
# scripts/upgrade-local-db.sh (or .ps1), which calls this script. Safe to run again.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_password="${BAYFLOW_APP_DB_PASSWORD:-bayflow_app}" <<'SQL'
SELECT format('CREATE ROLE bayflow_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
              :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'bayflow_app') \gexec

SELECT format('ALTER ROLE bayflow_app PASSWORD %L', :'app_password') \gexec
SQL
