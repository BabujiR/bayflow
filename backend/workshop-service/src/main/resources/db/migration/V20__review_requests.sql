-- Google review requests: a while after an invoice is fully paid, the customer is e-mailed the
-- location's Google review link (once per invoice). The sender skips a customer asked in the last
-- 90 days, or one who clicked "stop" (ws_review_opt_outs, by e-mail address). The stop link's
-- token is stored only as its SHA-256.
CREATE TABLE ws_review_requests (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    invoice_id      uuid         NOT NULL REFERENCES ws_invoices (id),
    location_id     uuid         NOT NULL,
    customer_email  varchar(254),
    customer_name   varchar(100) NOT NULL,
    vehicle         varchar(150),
    status          varchar(20)  NOT NULL,
    due_at          timestamptz,
    attempts        integer      NOT NULL DEFAULT 0,
    reason          varchar(200),
    sent_at         timestamptz,
    sent_by         varchar(100),
    token_hash      varchar(64),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_ws_review_requests_invoice UNIQUE (invoice_id),
    CONSTRAINT uq_ws_review_requests_token UNIQUE (token_hash),
    CONSTRAINT ck_ws_review_requests_status CHECK (status IN ('SCHEDULED', 'SENT', 'SKIPPED', 'CANCELLED')),
    CONSTRAINT ck_ws_review_requests_due CHECK (status <> 'SCHEDULED' OR due_at IS NOT NULL),
    CONSTRAINT ck_ws_review_requests_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX ix_ws_review_requests_due ON ws_review_requests (due_at) WHERE status = 'SCHEDULED';
CREATE INDEX ix_ws_review_requests_customer ON ws_review_requests (tenant_id, customer_email, sent_at DESC)
    WHERE status = 'SENT';

CREATE TABLE ws_review_opt_outs (
    tenant_id      uuid         NOT NULL,
    customer_email varchar(254) NOT NULL,
    created_at     timestamptz  NOT NULL,
    PRIMARY KEY (tenant_id, customer_email)
);

GRANT SELECT, INSERT, UPDATE ON ws_review_requests TO ${app_role};
GRANT SELECT, INSERT ON ws_review_opt_outs TO ${app_role};

ALTER TABLE ws_review_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_review_requests
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE ws_review_opt_outs ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_review_opt_outs
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The workshop of a stop link: the public page has nobody signed in. Only the workshop id comes back.
CREATE FUNCTION ws_review_request_tenant(hash varchar)
    RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT r.tenant_id FROM ws_review_requests r WHERE r.token_hash = hash
$$;

REVOKE ALL ON FUNCTION ws_review_request_tenant(varchar) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_review_request_tenant(varchar) TO ${app_role};

-- Requests due to go out, in every workshop (ids and workshops only).
CREATE FUNCTION ws_due_review_requests(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT r.id, r.tenant_id
    FROM ws_review_requests r
    WHERE r.status = 'SCHEDULED' AND r.due_at <= due_before
    ORDER BY r.due_at
    LIMIT max_rows
$$;

REVOKE ALL ON FUNCTION ws_due_review_requests(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_due_review_requests(timestamptz, integer) TO ${app_role};
