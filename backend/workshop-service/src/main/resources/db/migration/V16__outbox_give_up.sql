-- The outbox gives up on a message after too many failed attempts (bayflow.workshop.notifications
-- .max-attempts): failed_at is set, it is no longer retried, and an error is logged for an
-- operator. The message stays, with its last error, so it can be looked at or retried by hand
-- (UPDATE ws_outbox SET failed_at = NULL, attempts = 0, next_attempt_at = now() WHERE id = ...).
ALTER TABLE ws_outbox ADD COLUMN failed_at timestamptz;

CREATE OR REPLACE FUNCTION ws_due_outbox(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT o.id, o.tenant_id
    FROM ws_outbox o
    WHERE o.processed_at IS NULL AND o.failed_at IS NULL AND o.next_attempt_at <= due_before
    ORDER BY o.next_attempt_at
    LIMIT max_rows
$$;
