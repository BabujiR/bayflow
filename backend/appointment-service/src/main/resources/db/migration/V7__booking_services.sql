-- Online booking, step 1: each shop's own list of services customers can book (name, short
-- description, price shown as "from $54" / "$79" / "Free" / "Ask for a quote" / nothing, typical
-- time), in the shop's order, optionally for one location only. "Something else" is not stored:
-- the booking page always offers it. A new shop starts with a few common services (no prices).
CREATE TABLE apt_booking_services (
    id               uuid          PRIMARY KEY,
    tenant_id        uuid          NOT NULL,
    -- Null: offered at every location.
    location_id      uuid,
    -- Stable link name, e.g. "oil-change" (/book?service=oil-change); never changes.
    code             varchar(40)   NOT NULL,
    name             varchar(80)   NOT NULL,
    description      varchar(200),
    price_kind       varchar(10)   NOT NULL,
    price            numeric(10, 2),
    duration_minutes integer,
    active           boolean       NOT NULL DEFAULT true,
    sort_order       integer       NOT NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_apt_booking_services_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_apt_booking_services_price_kind CHECK (price_kind IN ('NONE', 'FROM', 'FIXED', 'FREE', 'QUOTE')),
    CONSTRAINT ck_apt_booking_services_price CHECK (
        (price_kind IN ('FROM', 'FIXED') AND price IS NOT NULL AND price >= 0)
        OR (price_kind NOT IN ('FROM', 'FIXED') AND price IS NULL)),
    CONSTRAINT ck_apt_booking_services_duration CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 5 AND 1440),
    CONSTRAINT ck_apt_booking_services_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_apt_booking_services_order ON apt_booking_services (tenant_id, sort_order);

GRANT SELECT, INSERT, UPDATE ON apt_booking_services TO ${app_role};

-- Workshop-wide, like customers: which location offers a service is checked by the app.
ALTER TABLE apt_booking_services ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_booking_services
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
