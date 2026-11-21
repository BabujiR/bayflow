-- Several announcements per workshop (was one banner on auth_tenant_branding). Each has its
-- own message, style and optional dates; the app shows the live ones as one line with
-- "N more" (option D). Today's banner, if any, becomes the first announcement.

CREATE TABLE auth_tenant_announcements (
    id         uuid         PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES auth_tenants (id),
    text       varchar(200) NOT NULL,
    style      varchar(20)  NOT NULL DEFAULT 'INFO',
    starts_at  timestamptz,
    ends_at    timestamptz,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_auth_tenant_announcements_text CHECK (length(btrim(text)) > 0),
    CONSTRAINT ck_auth_tenant_announcements_style CHECK (style IN ('INFO', 'IMPORTANT')),
    CONSTRAINT ck_auth_tenant_announcements_window
        CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at),
    CONSTRAINT ck_auth_tenant_announcements_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_auth_tenant_announcements_tenant ON auth_tenant_announcements (tenant_id, created_at);

INSERT INTO auth_tenant_announcements (id, tenant_id, text, style, starts_at, ends_at, created_at, updated_at)
SELECT gen_random_uuid(), tenant_id, banner_text, banner_style, banner_starts_at, banner_ends_at, updated_at, updated_at
FROM auth_tenant_branding
WHERE banner_text IS NOT NULL AND length(btrim(banner_text)) > 0;

ALTER TABLE auth_tenant_branding
    DROP CONSTRAINT ck_auth_tenant_branding_style,
    DROP CONSTRAINT ck_auth_tenant_branding_window,
    DROP COLUMN banner_text,
    DROP COLUMN banner_style,
    DROP COLUMN banner_starts_at,
    DROP COLUMN banner_ends_at;

GRANT SELECT, INSERT, UPDATE, DELETE ON auth_tenant_announcements TO ${app_role};

ALTER TABLE auth_tenant_announcements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_tenant_announcements
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
