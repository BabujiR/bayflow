-- Audit trail: who changed which account, and how (roles, locations, enabled, password resets,
-- unlocks, photo removals), shown to a workshop's admins under Admin > Activity. Names are kept
-- as they were at the time, so an event still reads right after an account is renamed.
-- Append-only for the services: no UPDATE or DELETE grant.
CREATE TABLE auth_audit_events (
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES auth_tenants (id),
    occurred_at timestamptz  NOT NULL,
    -- Who did it: the signed-in user (null for the system).
    actor_id    uuid,
    actor_name  varchar(100),
    action      varchar(40)  NOT NULL,
    target_id   uuid,
    target_name varchar(100),
    details     varchar(500),
    -- The id of the request (X-Request-Id), to find its log lines.
    request_id  varchar(64)
);

CREATE INDEX ix_auth_audit_events_recent ON auth_audit_events (tenant_id, occurred_at DESC);
CREATE INDEX ix_auth_audit_events_target ON auth_audit_events (target_id, occurred_at DESC);

GRANT SELECT, INSERT ON auth_audit_events TO ${app_role};

ALTER TABLE auth_audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_audit_events
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
