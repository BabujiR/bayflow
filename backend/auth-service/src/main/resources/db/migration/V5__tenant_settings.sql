-- Multi-tenancy, phase 3: each workshop's own settings (display name, contact details, labour
-- rate, currency, time zone). They replace the BAYFLOW_LABOUR_RATE, BAYFLOW_CURRENCY and
-- BAYFLOW_WORKSHOP_ZONE environment variables, which now only give a new workshop its
-- starting values. The default workshop starts with the values configured today.

CREATE TABLE auth_tenant_settings (
    id           uuid          PRIMARY KEY,
    tenant_id    uuid          NOT NULL REFERENCES auth_tenants (id),
    display_name varchar(100)  NOT NULL,
    phone        varchar(30),
    email        varchar(254),
    address      varchar(300),
    labour_rate  numeric(10,2) NOT NULL,
    currency     varchar(3)    NOT NULL,
    time_zone    varchar(64)   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_tenant_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_auth_tenant_settings_rate CHECK (labour_rate >= 0),
    CONSTRAINT ck_auth_tenant_settings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_auth_tenant_settings_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

INSERT INTO auth_tenant_settings (id, tenant_id, display_name, labour_rate, currency, time_zone, updated_at)
SELECT gen_random_uuid(), t.id, t.name, ${default_labour_rate}, '${default_currency}', '${default_time_zone}', now()
FROM auth_tenants t;

GRANT SELECT, INSERT, UPDATE ON auth_tenant_settings TO ${app_role};

ALTER TABLE auth_tenant_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_tenant_settings
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
