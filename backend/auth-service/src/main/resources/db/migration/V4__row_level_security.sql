-- Multi-tenancy, phase 2: the database itself keeps workshops apart (row-level security).
--
-- The services run as ${app_role}: not the owner of these tables, not a superuser, no
-- BYPASSRLS, so every policy below applies to them. Each transaction names its workshop with
-- set_config('app.tenant', <tenant id>, true) (common-security, TenantAwareJpaTransactionManager).
-- With no workshop set, NULLIF(...) is null and no row matches: a missing tenant fails closed.
-- RLS is ENABLEd, not FORCEd: the owner (Flyway's user) is exempt, so migrations and the few
-- owner-run SECURITY DEFINER functions still see all rows. The services refuse to start as the
-- owner (RowLevelSecurityGuard).

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${app_role};

-- Platform tables: no workshop, no RLS. The app reads tenants and keeps service clients in sync.
GRANT SELECT, INSERT, UPDATE ON auth_tenants, auth_clients TO ${app_role};

-- Per-workshop tables.
GRANT SELECT, INSERT, UPDATE, DELETE ON auth_users, auth_user_roles, auth_refresh_tokens TO ${app_role};

ALTER TABLE auth_users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_users
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE auth_user_roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_user_roles
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE auth_refresh_tokens ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
