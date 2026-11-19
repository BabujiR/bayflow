-- Notifications (phase N1): what each user is told when bookings and job cards change.
--
-- appointment-service and workshop-service send events (from their outboxes); this service
-- decides who is told, writes one notification per recipient and keeps the event's id so a
-- repeated delivery is stored once. Every row belongs to a workshop (tenant_id) and to one
-- recipient (a user id from auth-service).

CREATE TABLE ntf_notifications (
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NOT NULL,
    -- The location the event happened at (null if unknown); shown when an organization has several.
    location_id  uuid,
    recipient_id uuid         NOT NULL,
    type         varchar(40)  NOT NULL,
    title        varchar(200) NOT NULL,
    body         varchar(500) NOT NULL,
    -- A path in the web app, e.g. /job-cards/<id>.
    link         varchar(300),
    event_id     uuid         NOT NULL,
    created_at   timestamptz  NOT NULL,
    read_at      timestamptz,
    CONSTRAINT uq_ntf_notifications_event_recipient UNIQUE (tenant_id, event_id, recipient_id),
    CONSTRAINT ck_ntf_notifications_link CHECK (link IS NULL OR (link LIKE '/%' AND link NOT LIKE '//%')),
    CONSTRAINT ck_ntf_notifications_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- A user's list (newest first) and their unread count.
CREATE INDEX ix_ntf_notifications_recipient ON ntf_notifications (tenant_id, recipient_id, created_at DESC, id DESC);
CREATE INDEX ix_ntf_notifications_unread ON ntf_notifications (tenant_id, recipient_id) WHERE read_at IS NULL;
-- The nightly clean-up.
CREATE INDEX ix_ntf_notifications_created ON ntf_notifications (created_at);

-- Events already handled: a repeated delivery (the sender's outbox retried) is ignored.
CREATE TABLE ntf_received_events (
    event_id    uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL,
    type        varchar(40) NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT ck_ntf_received_events_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);
CREATE INDEX ix_ntf_received_events_received ON ntf_received_events (received_at);

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON ntf_notifications TO ${app_role};
GRANT SELECT, INSERT ON ntf_received_events TO ${app_role};

-- Row-level security. Besides the workshop (app.tenant), each transaction names whose
-- notifications it may touch in app.recipient: a user id (a signed-in user: only their own),
-- '*' (this service's own work: storing an event's notifications) or nothing (no rows).
ALTER TABLE ntf_notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_recipient_isolation ON ntf_notifications
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.recipient', true) = '*' THEN true
                    WHEN coalesce(current_setting('app.recipient', true), '') = '' THEN false
                    ELSE recipient_id = current_setting('app.recipient', true)::uuid
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.recipient', true) = '*' THEN true
                    WHEN coalesce(current_setting('app.recipient', true), '') = '' THEN false
                    ELSE recipient_id = current_setting('app.recipient', true)::uuid
               END);

ALTER TABLE ntf_received_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ntf_received_events
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The nightly clean-up works across workshops, which row-level security does not allow the
-- app login; this owner-run function deletes old rows only and returns how many notifications went.
CREATE FUNCTION ntf_delete_before(cutoff timestamptz)
    RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
DECLARE
    removed integer;
BEGIN
    DELETE FROM ntf_notifications WHERE created_at < cutoff;
    GET DIAGNOSTICS removed = ROW_COUNT;
    DELETE FROM ntf_received_events WHERE received_at < cutoff;
    RETURN removed;
END;
$$;

REVOKE ALL ON FUNCTION ntf_delete_before(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ntf_delete_before(timestamptz) TO ${app_role};
