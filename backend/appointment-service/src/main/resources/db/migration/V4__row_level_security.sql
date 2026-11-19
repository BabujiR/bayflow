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

GRANT SELECT, INSERT, UPDATE, DELETE ON apt_customers, apt_vehicles, apt_appointments, apt_outbox
    TO ${app_role};

ALTER TABLE apt_customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_customers
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE apt_vehicles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_vehicles
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE apt_appointments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_appointments
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE apt_outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_outbox
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The outbox retry job is the one reader across workshops. This owner-run function returns
-- only the id and workshop of messages that are due; the job then handles each message inside
-- its own workshop, under the policies above.
CREATE FUNCTION apt_due_outbox(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT o.id, o.tenant_id
    FROM apt_outbox o
    WHERE o.processed_at IS NULL AND o.next_attempt_at <= due_before
    ORDER BY o.next_attempt_at
    LIMIT max_rows
$$;

REVOKE ALL ON FUNCTION apt_due_outbox(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION apt_due_outbox(timestamptz, integer) TO ${app_role};
