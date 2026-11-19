-- Workshop branding: each workshop's customers see its own logo, colour, sign-in cover photo
-- and announcement banner instead of Bayflow's. One branding row per workshop; images (logo,
-- cover) are kept here locally, re-encoded on upload (S3 on AWS, see docs/SAAS-AWS-NOTES.md).

CREATE TABLE auth_tenant_branding (
    id               uuid         PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES auth_tenants (id),
    -- #RRGGBB, or NULL for Bayflow's colours.
    primary_color    varchar(7),
    banner_text      varchar(200),
    banner_style     varchar(20)  NOT NULL DEFAULT 'INFO',
    banner_starts_at timestamptz,
    banner_ends_at   timestamptz,
    show_powered_by  boolean      NOT NULL DEFAULT true,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_tenant_branding_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_auth_tenant_branding_color CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9a-f]{6}$'),
    CONSTRAINT ck_auth_tenant_branding_style CHECK (banner_style IN ('INFO', 'IMPORTANT')),
    CONSTRAINT ck_auth_tenant_branding_window
        CHECK (banner_starts_at IS NULL OR banner_ends_at IS NULL OR banner_starts_at < banner_ends_at),
    CONSTRAINT ck_auth_tenant_branding_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE TABLE auth_tenant_assets (
    id           uuid        PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES auth_tenants (id),
    kind         varchar(10) NOT NULL,
    content_type varchar(20) NOT NULL,
    content      bytea       NOT NULL,
    -- SHA-256 of the content: the ETag and the cache-busting part of the image's address.
    sha256       varchar(64) NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT uq_auth_tenant_assets_kind UNIQUE (tenant_id, kind),
    CONSTRAINT ck_auth_tenant_assets_kind CHECK (kind IN ('LOGO', 'COVER')),
    CONSTRAINT ck_auth_tenant_assets_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_auth_tenant_assets_size CHECK (octet_length(content) <= 4194304),
    CONSTRAINT ck_auth_tenant_assets_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

GRANT SELECT, INSERT, UPDATE ON auth_tenant_branding TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON auth_tenant_assets TO ${app_role};

ALTER TABLE auth_tenant_branding ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_tenant_branding
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);

ALTER TABLE auth_tenant_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_tenant_assets
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
