-- R2b: QuickBooks Online. One connection per organization (its company, tokens encrypted by
-- the application, and which QuickBooks products/services Ramplight's lines are booked to),
-- short-lived sign-in states, and on invoices and payments the ids they got in QuickBooks.

CREATE TABLE ws_qbo_connections (
    id                    uuid          PRIMARY KEY,
    tenant_id             uuid          NOT NULL UNIQUE,
    realm_id              varchar(32)   NOT NULL,
    environment           varchar(12)   NOT NULL,
    company_name          varchar(200),
    country               varchar(8),
    -- AES-GCM ciphertexts (key: BAYFLOW_QBO_TOKEN_KEY), never the tokens themselves.
    access_token          text          NOT NULL,
    access_expires_at     timestamptz   NOT NULL,
    refresh_token         text          NOT NULL,
    refresh_expires_at    timestamptz,
    parts_item_id         varchar(32),
    parts_item_name       varchar(200),
    labour_item_id        varchar(32),
    labour_item_name      varchar(200),
    fees_item_id          varchar(32),
    fees_item_name        varchar(200),
    deposit_account_id    varchar(32),
    deposit_account_name  varchar(200),
    connected_at          timestamptz   NOT NULL,
    connected_by          varchar(100)  NOT NULL,
    updated_at            timestamptz   NOT NULL,
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_ws_qbo_connections_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- "Connect to QuickBooks" in progress: the hash of the state sent to Intuit, for 10 minutes.
CREATE TABLE ws_qbo_oauth_states (
    id          uuid          PRIMARY KEY,
    tenant_id   uuid          NOT NULL,
    state_hash  varchar(64)   NOT NULL UNIQUE,
    return_url  varchar(500)  NOT NULL,
    created_by  varchar(100)  NOT NULL,
    expires_at  timestamptz   NOT NULL,
    created_at  timestamptz   NOT NULL,
    CONSTRAINT ck_ws_qbo_oauth_states_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

ALTER TABLE ws_invoices
    -- The company it was sent to: connected to another company later, it counts as not sent.
    ADD COLUMN qbo_realm_id    varchar(32),
    ADD COLUMN qbo_invoice_id  varchar(32),
    ADD COLUMN qbo_customer_id varchar(32),
    ADD COLUMN qbo_synced_at   timestamptz,
    ADD COLUMN qbo_voided_at   timestamptz,
    ADD COLUMN qbo_error       varchar(500);

ALTER TABLE ws_payments
    ADD COLUMN qbo_payment_id  varchar(32);

GRANT SELECT, INSERT, UPDATE, DELETE ON ws_qbo_connections, ws_qbo_oauth_states TO ${app_role};

ALTER TABLE ws_qbo_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_qbo_oauth_states ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ws_qbo_connections
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

CREATE POLICY tenant_isolation ON ws_qbo_oauth_states
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
