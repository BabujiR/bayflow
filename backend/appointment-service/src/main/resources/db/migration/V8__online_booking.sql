-- Online booking, step 2: customers book on the public page without signing in.

-- A car booked online is known by its VIN, or by its year, make and model; the plate (and the
-- VIN, if not given) are filled in at check-in, before the job card opens.
ALTER TABLE apt_vehicles
    ALTER COLUMN vin DROP NOT NULL,
    ALTER COLUMN registration DROP NOT NULL,
    ALTER COLUMN make DROP NOT NULL,
    ALTER COLUMN model DROP NOT NULL,
    ALTER COLUMN model_year DROP NOT NULL;
ALTER TABLE apt_vehicles ADD CONSTRAINT ck_apt_vehicles_identified
    CHECK (vin IS NOT NULL OR (make IS NOT NULL AND model IS NOT NULL AND model_year IS NOT NULL));

-- An online booking keeps its service as it was offered, and when the customer agreed to texts.
ALTER TABLE apt_appointments
    ADD COLUMN booked_online      boolean       NOT NULL DEFAULT false,
    ADD COLUMN service_name       varchar(80),
    ADD COLUMN service_price_kind varchar(10),
    ADD COLUMN service_price      numeric(10, 2),
    ADD COLUMN sms_consent_at     timestamptz;

CREATE INDEX ix_apt_customers_email ON apt_customers (tenant_id, email);

-- When each location takes online bookings: its weekly hours ("08:00-17:00" per weekday,
-- Monday first, empty = closed), the slot length, how many cars may arrive per slot, the notice
-- needed and how far ahead. A location without a row uses the defaults (Mon-Fri 8-5).
CREATE TABLE apt_booking_settings (
    location_id        uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL,
    weekly_hours       varchar(120) NOT NULL,
    slot_minutes       integer      NOT NULL,
    cars_per_slot      integer      NOT NULL,
    min_notice_minutes integer      NOT NULL,
    max_days_ahead     integer      NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_apt_booking_settings_slot CHECK (slot_minutes IN (15, 20, 30, 45, 60, 90, 120)),
    CONSTRAINT ck_apt_booking_settings_cars CHECK (cars_per_slot BETWEEN 1 AND 50),
    CONSTRAINT ck_apt_booking_settings_notice CHECK (min_notice_minutes BETWEEN 0 AND 10080),
    CONSTRAINT ck_apt_booking_settings_ahead CHECK (max_days_ahead BETWEEN 1 AND 365),
    CONSTRAINT ck_apt_booking_settings_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

GRANT SELECT, INSERT, UPDATE ON apt_booking_settings TO ${app_role};

ALTER TABLE apt_booking_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apt_booking_settings
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
