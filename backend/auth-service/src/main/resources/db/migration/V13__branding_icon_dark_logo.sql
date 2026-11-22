-- Branding, phase 2: a square icon (browser tab, collapsed sidebar, phone header) and a logo
-- for dark mode, stored with the logo and cover photo.

ALTER TABLE auth_tenant_assets DROP CONSTRAINT ck_auth_tenant_assets_kind;
ALTER TABLE auth_tenant_assets ADD CONSTRAINT ck_auth_tenant_assets_kind
    CHECK (kind IN ('LOGO', 'LOGO_DARK', 'ICON', 'COVER'));
