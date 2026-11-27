-- Estimate approval links: a private link e-mailed to the customer, to see their estimate and
-- approve or decline each item without an account, signing to confirm. Sending again replaces
-- the previous link (replaced_at). Only the token's SHA-256 is stored. Up to a few reminders
-- go out while nothing is decided (next_reminder_at). The signature is a small PNG.
CREATE TABLE ws_estimate_links (
    id               uuid         PRIMARY KEY,
    tenant_id        uuid         NOT NULL,
    job_card_id      uuid         NOT NULL REFERENCES ws_job_cards (id),
    token_hash       varchar(64)  NOT NULL,
    sent_to          varchar(254) NOT NULL,
    sent_by          varchar(100) NOT NULL,
    sent_at          timestamptz  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    first_viewed_at  timestamptz,
    reminders_sent   integer      NOT NULL DEFAULT 0,
    next_reminder_at timestamptz,
    replaced_at      timestamptz,
    decided_at       timestamptz,
    signer_name      varchar(100),
    signature        bytea,
    signer_ip        varchar(64),
    signer_agent     varchar(300),
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_ws_estimate_links_token UNIQUE (token_hash),
    CONSTRAINT ck_ws_estimate_links_signature CHECK (signature IS NULL OR octet_length(signature) <= 204800),
    CONSTRAINT ck_ws_estimate_links_signed CHECK ((decided_at IS NULL) = (signature IS NULL))
);

CREATE INDEX ix_ws_estimate_links_job_card ON ws_estimate_links (job_card_id, sent_at DESC);
CREATE INDEX ix_ws_estimate_links_reminders ON ws_estimate_links (next_reminder_at)
    WHERE decided_at IS NULL AND replaced_at IS NULL;

GRANT SELECT, INSERT, UPDATE ON ws_estimate_links TO ${app_role};

ALTER TABLE ws_estimate_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ws_estimate_links
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The workshop of a link: the public page has nobody signed in. Only the workshop id comes back.
CREATE FUNCTION ws_estimate_link_tenant(hash varchar)
    RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT l.tenant_id FROM ws_estimate_links l WHERE l.token_hash = hash
$$;

REVOKE ALL ON FUNCTION ws_estimate_link_tenant(varchar) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_estimate_link_tenant(varchar) TO ${app_role};

-- Links due a reminder, in every workshop (ids and workshops only).
CREATE FUNCTION ws_due_estimate_reminders(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT l.id, l.tenant_id
    FROM ws_estimate_links l
    WHERE l.decided_at IS NULL AND l.replaced_at IS NULL AND l.expires_at > due_before
      AND l.next_reminder_at <= due_before
    ORDER BY l.next_reminder_at
    LIMIT max_rows
$$;

REVOKE ALL ON FUNCTION ws_due_estimate_reminders(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ws_due_estimate_reminders(timestamptz, integer) TO ${app_role};
