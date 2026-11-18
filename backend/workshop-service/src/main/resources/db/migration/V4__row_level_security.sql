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

GRANT SELECT, INSERT, UPDATE, DELETE ON ws_job_cards, ws_inspection_findings, ws_recommended_items,
    ws_work_logs, ws_part_usages, ws_labour_entries, ws_service_history TO ${app_role};

ALTER TABLE ws_job_cards ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_job_cards
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_inspection_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_inspection_findings
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_recommended_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_recommended_items
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_work_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_work_logs
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_part_usages ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_part_usages
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_labour_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_labour_entries
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_service_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_service_history
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
