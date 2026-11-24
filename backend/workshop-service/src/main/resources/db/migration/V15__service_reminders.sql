-- R3b step 2: reminders to customers. Customers can turn them off; the daily run finds the
-- organizations with something to send through an owner-run function (row-level security
-- otherwise keeps each organization's rows to itself).

CREATE TABLE ws_reminder_opt_outs (
    tenant_id         uuid         NOT NULL,
    customer_user_id  uuid         NOT NULL,
    created_at        timestamptz  NOT NULL,
    PRIMARY KEY (tenant_id, customer_user_id),
    CONSTRAINT ck_ws_reminder_opt_outs_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

GRANT SELECT, INSERT, DELETE ON ws_reminder_opt_outs TO ${app_role};

ALTER TABLE ws_reminder_opt_outs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ws_reminder_opt_outs
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- Organizations with a not-yet-reminded service due before due_before (the widest window).
CREATE FUNCTION ws_due_reminder_tenants(due_before timestamptz)
    RETURNS TABLE (tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT DISTINCT v.tenant_id
    FROM ws_vehicle_services v
    WHERE v.reminded_at IS NULL AND v.customer_user_id IS NOT NULL AND v.due_at < due_before
$$;

REVOKE ALL ON FUNCTION ws_due_reminder_tenants(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_due_reminder_tenants(timestamptz) TO ${app_role};
