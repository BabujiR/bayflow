-- Notifications, phase N1: events for notification-service (estimate ready, decided, work
-- complete), saved in the same transaction as the job-card change that caused them
-- (transactional outbox) and delivered afterwards with retries, like appointment-service's.

CREATE TABLE ws_outbox (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    job_card_id     uuid          NOT NULL REFERENCES ws_job_cards (id),
    type            varchar(50)   NOT NULL,
    payload         text          NOT NULL,
    attempts        integer       NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    last_error      varchar(1000),
    processed_at    timestamptz,
    created_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_ws_outbox_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- The dispatcher only ever looks at undelivered messages.
CREATE INDEX ix_ws_outbox_pending ON ws_outbox (next_attempt_at) WHERE processed_at IS NULL;
CREATE INDEX ix_ws_outbox_job_card ON ws_outbox (tenant_id, job_card_id);

GRANT SELECT, INSERT, UPDATE ON ws_outbox TO ${app_role};

ALTER TABLE ws_outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_outbox
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The retry job is the one reader across workshops: ids and workshops of due messages only.
CREATE FUNCTION ws_due_outbox(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT o.id, o.tenant_id
    FROM ws_outbox o
    WHERE o.processed_at IS NULL AND o.next_attempt_at <= due_before
    ORDER BY o.next_attempt_at
    LIMIT max_rows
$$;

REVOKE ALL ON FUNCTION ws_due_outbox(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_due_outbox(timestamptz, integer) TO ${app_role};
