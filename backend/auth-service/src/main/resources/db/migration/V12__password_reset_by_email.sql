-- Account recovery by e-mail. "Forgot password?" sends a single-use link that sets a new
-- password; only a hash of the link's token is stored. E-mails go through a transactional
-- outbox (auth_mail_outbox): saved with the change that caused them, sent right after it commits
-- and retried until delivered (or given up after an hour, when a reset link has expired anyway).

CREATE TABLE auth_password_resets (
    id           uuid        PRIMARY KEY,
    tenant_id    uuid        NOT NULL,
    user_id      uuid        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT uq_auth_password_resets_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_password_resets_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_auth_password_resets_user ON auth_password_resets (tenant_id, user_id);

CREATE TABLE auth_mail_outbox (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    recipient       varchar(254)  NOT NULL,
    sender_name     varchar(200)  NOT NULL,
    reply_to        varchar(254),
    subject         varchar(300)  NOT NULL,
    -- Cleared once sent: a reset link must not stay in the database.
    text_body       text          NOT NULL,
    html_body       text          NOT NULL,
    attempts        integer       NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    last_error      varchar(1000),
    processed_at    timestamptz,
    created_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_auth_mail_outbox_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_auth_mail_outbox_pending ON auth_mail_outbox (next_attempt_at) WHERE processed_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON auth_password_resets TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON auth_mail_outbox TO ${app_role};

ALTER TABLE auth_password_resets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_password_resets
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE auth_mail_outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_mail_outbox
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- The retry job is the one reader across workshops: ids and workshops of due e-mails only.
CREATE FUNCTION auth_due_mail(due_before timestamptz, max_rows integer)
    RETURNS TABLE (id uuid, tenant_id uuid)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT o.id, o.tenant_id
    FROM auth_mail_outbox o
    WHERE o.processed_at IS NULL AND o.next_attempt_at <= due_before
    ORDER BY o.next_attempt_at
    LIMIT max_rows
$$;

REVOKE ALL ON FUNCTION auth_due_mail(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_due_mail(timestamptz, integer) TO ${app_role};
