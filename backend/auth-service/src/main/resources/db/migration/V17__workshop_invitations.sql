-- Onboarding step 2: workshops join by invitation. Ramplight's team sends a setup link from the
-- console (<base domain>/setup?invite=…, valid 7 days, single use); the workshop owner opens it
-- and creates the workshop and their admin account. Only a hash of the link's token is stored.
-- Platform-level, like demo requests: no tenant_id and no row-level security.
CREATE TABLE auth_invitations (
    id              uuid          PRIMARY KEY,
    token_hash      varchar(64)   NOT NULL,
    email           varchar(254)  NOT NULL,
    contact_name    varchar(120),
    shop_name       varchar(120),
    demo_request_id uuid          REFERENCES auth_demo_requests (id) ON DELETE SET NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',
    expires_at      timestamptz   NOT NULL,
    sent_count      integer       NOT NULL DEFAULT 1,
    last_sent_at    timestamptz   NOT NULL,
    created_at      timestamptz   NOT NULL,
    created_by      varchar(100)  NOT NULL,
    -- The workshop it created (set on acceptance).
    accepted_at     timestamptz,
    tenant_id       uuid          REFERENCES auth_tenants (id) ON DELETE SET NULL,
    workshop_slug   varchar(30),
    revoked_at      timestamptz,
    revoked_by      varchar(100),
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_invitations_token UNIQUE (token_hash),
    CONSTRAINT ck_auth_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);

CREATE INDEX ix_auth_invitations_created ON auth_invitations (created_at DESC);
CREATE INDEX ix_auth_invitations_pending_email ON auth_invitations (email) WHERE status = 'PENDING';

GRANT SELECT, INSERT, UPDATE ON auth_invitations TO ${app_role};
