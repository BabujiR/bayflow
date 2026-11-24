-- R3b: maintenance reminders from the shop's own intervals (no licensed schedules). An interval
-- ("Oil change: 5,000 mi or 6 months") is matched to finished work by canned job or keyword;
-- each vehicle keeps, per interval, when it was last done and when it is next due.

CREATE TABLE ws_service_intervals (
    id              uuid           PRIMARY KEY,
    tenant_id       uuid           NOT NULL,
    name            varchar(80)    NOT NULL,
    miles           integer        CHECK (miles BETWEEN 100 AND 200000),
    months          integer        CHECK (months BETWEEN 1 AND 120),
    -- Comma-separated, lower case: finished work whose description contains one counts.
    keywords        varchar(500),
    -- Comma-separated ids of the canned jobs that count as this service.
    canned_job_ids  varchar(2000),
    position        integer        NOT NULL DEFAULT 0,
    active          boolean        NOT NULL DEFAULT true,
    created_at      timestamptz    NOT NULL,
    updated_at      timestamptz    NOT NULL,
    version         bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_ws_service_intervals_every CHECK (miles IS NOT NULL OR months IS NOT NULL),
    CONSTRAINT ck_ws_service_intervals_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- Per organization: reminders on or off, how early, the miles a day assumed without history,
-- and whether the starter intervals were added (once, so deleting them sticks).
CREATE TABLE ws_reminder_settings (
    tenant_id          uuid         PRIMARY KEY,
    enabled            boolean      NOT NULL DEFAULT true,
    remind_days_before integer      NOT NULL DEFAULT 14 CHECK (remind_days_before BETWEEN 0 AND 90),
    miles_per_day      integer      NOT NULL DEFAULT 30 CHECK (miles_per_day BETWEEN 1 AND 500),
    defaults_added     boolean      NOT NULL DEFAULT false,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ws_reminder_settings_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

-- One row per vehicle and interval: last done (where, when, at what mileage) and next due.
CREATE TABLE ws_vehicle_services (
    id                uuid          PRIMARY KEY,
    tenant_id         uuid          NOT NULL,
    location_id       uuid          NOT NULL,
    vehicle_id        uuid          NOT NULL,
    interval_id       uuid          NOT NULL REFERENCES ws_service_intervals (id) ON DELETE CASCADE,
    customer_user_id  uuid,
    customer_name     varchar(100)  NOT NULL,
    registration      varchar(20)   NOT NULL,
    job_card_id       uuid          NOT NULL,
    last_done_at      timestamptz   NOT NULL,
    last_mileage      integer       NOT NULL,
    due_mileage       integer,
    -- Whichever comes first: the months, or the miles at the vehicle's miles a day.
    due_at            timestamptz   NOT NULL,
    reminded_at       timestamptz,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT uq_ws_vehicle_services UNIQUE (vehicle_id, interval_id),
    CONSTRAINT ck_ws_vehicle_services_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_ws_vehicle_services_due ON ws_vehicle_services (tenant_id, due_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON ws_service_intervals, ws_reminder_settings, ws_vehicle_services
    TO ${app_role};

ALTER TABLE ws_service_intervals ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_reminder_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_vehicle_services ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ws_service_intervals
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

CREATE POLICY tenant_isolation ON ws_reminder_settings
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

-- Organization-wide (a vehicle may be serviced at another location next time); the Due soon
-- list keeps staff to their own locations' vehicles.
CREATE POLICY tenant_isolation ON ws_vehicle_services
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
