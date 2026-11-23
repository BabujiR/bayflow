-- R3a: canned jobs, the shop's ready-made packages of work ("Synthetic oil change"): a
-- description, labour hours and the usual parts. location_id NULL = every location. A
-- recommendation remembers the canned job it was made from.

CREATE TABLE ws_canned_jobs (
    id              uuid           PRIMARY KEY,
    tenant_id       uuid           NOT NULL,
    location_id     uuid,
    name            varchar(120)   NOT NULL,
    category        varchar(60),
    description     varchar(1000)  NOT NULL,
    labour_hours    numeric(6, 2)  NOT NULL CHECK (labour_hours >= 0),
    active          boolean        NOT NULL DEFAULT true,
    created_at      timestamptz    NOT NULL,
    created_by      varchar(100)   NOT NULL,
    updated_at      timestamptz    NOT NULL,
    version         bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_ws_canned_jobs_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_ws_canned_jobs_tenant_name ON ws_canned_jobs (tenant_id, lower(name));

CREATE TABLE ws_canned_job_parts (
    id              uuid           PRIMARY KEY,
    tenant_id       uuid           NOT NULL,
    canned_job_id   uuid           NOT NULL REFERENCES ws_canned_jobs (id) ON DELETE CASCADE,
    position        integer        NOT NULL,
    part_number     varchar(50)    NOT NULL,
    name            varchar(200)   NOT NULL,
    quantity        integer        NOT NULL CHECK (quantity BETWEEN 1 AND 1000),
    unit_price      numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT ck_ws_canned_job_parts_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_ws_canned_job_parts_job ON ws_canned_job_parts (canned_job_id, position);

ALTER TABLE ws_recommended_items ADD COLUMN canned_job_id uuid;

GRANT SELECT, INSERT, UPDATE, DELETE ON ws_canned_jobs, ws_canned_job_parts TO ${app_role};

ALTER TABLE ws_canned_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE ws_canned_job_parts ENABLE ROW LEVEL SECURITY;

-- The organization's jobs for every location, and those of the locations the caller works at.
CREATE POLICY tenant_location_isolation ON ws_canned_jobs
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND (location_id IS NULL
                OR CASE WHEN current_setting('app.locations', true) = '*' THEN true
                        ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
                   END))
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND (location_id IS NULL
                OR CASE WHEN current_setting('app.locations', true) = '*' THEN true
                        ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
                   END));

CREATE POLICY tenant_isolation ON ws_canned_job_parts
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
