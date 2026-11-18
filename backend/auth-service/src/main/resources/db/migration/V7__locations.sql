-- Organizations and locations, phase L1: a tenant (organization) has one or more locations
-- (shops). Every existing organization gets one location, "Main", whose id is the tenant id:
-- the other services can then fill in location_id = tenant_id on their own rows without
-- reading this table. Main has no overrides, so it uses the organization's settings.

CREATE TABLE auth_locations (
    id          uuid          PRIMARY KEY,
    tenant_id   uuid          NOT NULL REFERENCES auth_tenants (id),
    code        varchar(20)   NOT NULL,
    name        varchar(100)  NOT NULL,
    phone       varchar(30),
    email       varchar(254),
    address     varchar(300),
    -- Overrides of the organization's settings; NULL = use the organization's.
    time_zone   varchar(64),
    labour_rate numeric(10,2),
    status      varchar(20)   NOT NULL,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_locations_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_auth_locations_code CHECK (code ~ '^[a-z0-9]([a-z0-9-]{0,18}[a-z0-9])?$' AND length(code) >= 2),
    CONSTRAINT ck_auth_locations_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_auth_locations_rate CHECK (labour_rate IS NULL OR labour_rate >= 0),
    CONSTRAINT ck_auth_locations_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff')),
    CONSTRAINT ck_auth_locations_id
        CHECK (id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_auth_locations_tenant ON auth_locations (tenant_id, status);

INSERT INTO auth_locations (id, tenant_id, code, name, status, created_at, updated_at)
SELECT t.id, t.id, 'main', 'Main', 'ACTIVE', now(), now()
FROM auth_tenants t;

GRANT SELECT, INSERT, UPDATE ON auth_locations TO ${app_role};

ALTER TABLE auth_locations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_locations
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
