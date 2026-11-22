-- Invoicing (R2a): one invoice per completed job card (a new one only after a void), its
-- lines, the payments against it, and each organization's gap-free invoice numbers. Like the
-- job cards, every table carries tenant_id and location_id, so row-level security scopes it
-- to the workshop and to the staff member's locations. Part costs (for profit) are entered on
-- the invoice's lines, never by mechanics on the job card.

CREATE TABLE ws_invoices (
    id                    uuid          PRIMARY KEY,
    tenant_id             uuid          NOT NULL,
    location_id           uuid          NOT NULL,
    job_card_id           uuid          NOT NULL,
    -- Assigned when issued (gap-free per organization); null on drafts.
    number                varchar(20),
    status                varchar(20)   NOT NULL,
    -- Who and what is billed, copied when the draft is made.
    customer_user_id      uuid,
    customer_id           uuid,
    customer_name         varchar(100)  NOT NULL,
    customer_email        varchar(254),
    customer_phone        varchar(40),
    registration          varchar(20)   NOT NULL,
    vin                   varchar(17)   NOT NULL,
    mileage               integer       NOT NULL,
    vehicle_description   varchar(120),
    currency              varchar(3)    NOT NULL,
    -- The location's settings when the draft was made (percent; null fee = off).
    parts_tax_rate        numeric(6,3)  NOT NULL,
    labour_tax_rate       numeric(6,3)  NOT NULL,
    fees_tax_rate         numeric(6,3)  NOT NULL,
    shop_supplies_percent numeric(6,3),
    shop_supplies_cap     numeric(10,2),
    payment_terms_days    integer       NOT NULL,
    footer                varchar(500),
    notes                 varchar(1000),
    parts_total           numeric(12,2) NOT NULL DEFAULT 0,
    labour_total          numeric(12,2) NOT NULL DEFAULT 0,
    fees_total            numeric(12,2) NOT NULL DEFAULT 0,
    discount_total        numeric(12,2) NOT NULL DEFAULT 0,
    parts_tax             numeric(12,2) NOT NULL DEFAULT 0,
    labour_tax            numeric(12,2) NOT NULL DEFAULT 0,
    fees_tax              numeric(12,2) NOT NULL DEFAULT 0,
    tax_total             numeric(12,2) NOT NULL DEFAULT 0,
    total                 numeric(12,2) NOT NULL DEFAULT 0,
    amount_paid           numeric(12,2) NOT NULL DEFAULT 0,
    balance_due           numeric(12,2) NOT NULL DEFAULT 0,
    issued_at             timestamptz,
    issued_by             varchar(100),
    due_at                timestamptz,
    voided_at             timestamptz,
    voided_by             varchar(100),
    void_reason           varchar(300),
    created_at            timestamptz   NOT NULL,
    created_by            varchar(100)  NOT NULL,
    updated_at            timestamptz   NOT NULL,
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_ws_invoices_id_location UNIQUE (id, location_id),
    CONSTRAINT uq_ws_invoices_number UNIQUE (tenant_id, number),
    CONSTRAINT fk_ws_invoices_card_location
        FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id),
    CONSTRAINT ck_ws_invoices_status
        CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    CONSTRAINT ck_ws_invoices_number CHECK ((status = 'DRAFT') = (number IS NULL)),
    CONSTRAINT ck_ws_invoices_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- One live invoice per job card; a voided one makes room for its replacement.
CREATE UNIQUE INDEX uq_ws_invoices_job_card_live ON ws_invoices (job_card_id) WHERE status <> 'VOID';
CREATE INDEX ix_ws_invoices_status ON ws_invoices (tenant_id, location_id, status);
CREATE INDEX ix_ws_invoices_issued ON ws_invoices (tenant_id, issued_at) WHERE issued_at IS NOT NULL;
CREATE INDEX ix_ws_invoices_customer ON ws_invoices (customer_user_id);

CREATE TABLE ws_invoice_lines (
    id                     uuid          PRIMARY KEY,
    tenant_id              uuid          NOT NULL,
    location_id            uuid          NOT NULL,
    invoice_id             uuid          NOT NULL,
    position               integer       NOT NULL,
    type                   varchar(10)   NOT NULL,
    -- SHOP_SUPPLIES (kept at the location's share of labour) or DISPOSAL; null for others.
    fee_code               varchar(20),
    description            varchar(200)  NOT NULL,
    part_number            varchar(50),
    quantity               numeric(10,2) NOT NULL,
    unit_price             numeric(12,2) NOT NULL,
    unit_cost              numeric(12,2),
    line_total             numeric(12,2) NOT NULL,
    taxable                boolean       NOT NULL,
    tax_category           varchar(10)   NOT NULL,
    source_part_usage_id   uuid,
    source_labour_entry_id uuid,
    CONSTRAINT fk_ws_invoice_lines_invoice
        FOREIGN KEY (invoice_id, location_id) REFERENCES ws_invoices (id, location_id) ON DELETE CASCADE,
    CONSTRAINT ck_ws_invoice_lines_type CHECK (type IN ('PART', 'LABOUR', 'FEE', 'DISCOUNT')),
    CONSTRAINT ck_ws_invoice_lines_category CHECK (tax_category IN ('PARTS', 'LABOUR', 'FEES')),
    CONSTRAINT ck_ws_invoice_lines_amounts CHECK (quantity > 0 AND unit_price >= 0
        AND (unit_cost IS NULL OR unit_cost >= 0)),
    CONSTRAINT ck_ws_invoice_lines_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);
CREATE INDEX ix_ws_invoice_lines_invoice ON ws_invoice_lines (invoice_id, position);

CREATE TABLE ws_payments (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    location_id     uuid          NOT NULL,
    invoice_id      uuid          NOT NULL,
    method          varchar(20)   NOT NULL,
    -- Negative for a refund.
    amount          numeric(12,2) NOT NULL,
    reference       varchar(100),
    received_at     timestamptz   NOT NULL,
    received_by     varchar(100)  NOT NULL,
    idempotency_key varchar(100),
    created_at      timestamptz   NOT NULL,
    CONSTRAINT fk_ws_payments_invoice
        FOREIGN KEY (invoice_id, location_id) REFERENCES ws_invoices (id, location_id),
    CONSTRAINT uq_ws_payments_idempotency UNIQUE (invoice_id, idempotency_key),
    CONSTRAINT ck_ws_payments_method CHECK (method IN ('CASH', 'CARD', 'CHECK', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_ws_payments_amount CHECK (amount <> 0),
    CONSTRAINT ck_ws_payments_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);
CREATE INDEX ix_ws_payments_invoice ON ws_payments (invoice_id);
CREATE INDEX ix_ws_payments_received ON ws_payments (tenant_id, received_at);

-- The next invoice number of each organization; locked while an invoice is issued, so numbers
-- never repeat and never skip (a rolled-back issue gives its number back).
CREATE TABLE ws_invoice_counters (
    tenant_id   uuid   PRIMARY KEY,
    next_number bigint NOT NULL,
    CONSTRAINT ck_ws_invoice_counters_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

GRANT SELECT, INSERT, UPDATE ON ws_payments, ws_invoice_counters TO ${app_role};
-- Drafts can be discarded (deleted with their lines); issued invoices are only ever voided.
GRANT SELECT, INSERT, UPDATE, DELETE ON ws_invoices, ws_invoice_lines TO ${app_role};

ALTER TABLE ws_invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_invoice_counters ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_location_isolation ON ws_invoices
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

CREATE POLICY tenant_location_isolation ON ws_invoice_lines
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

CREATE POLICY tenant_location_isolation ON ws_payments
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

-- Numbers count across all of the organization's locations.
CREATE POLICY tenant_isolation ON ws_invoice_counters
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
