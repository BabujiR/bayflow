-- Multi-tenancy, phase 1: every user, role and refresh token belongs to a workshop (tenant).
-- Existing rows move into the "default" workshop, whose id is the same fixed value in every
-- service (Flyway placeholder default_tenant_id).

CREATE TABLE auth_tenants (
    id         uuid         PRIMARY KEY,
    slug       varchar(30)  NOT NULL,
    name       varchar(100) NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_auth_tenants_slug CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'),
    CONSTRAINT ck_auth_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

INSERT INTO auth_tenants (id, slug, name, status, created_at, updated_at)
VALUES ('${default_tenant_id}', 'default', 'Bayflow workshop', 'ACTIVE', now(), now());

-- Users: tenant column, then usernames and emails unique per workshop instead of globally.
ALTER TABLE auth_users ADD COLUMN tenant_id uuid;
UPDATE auth_users SET tenant_id = '${default_tenant_id}';
ALTER TABLE auth_users
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_auth_users_tenant FOREIGN KEY (tenant_id) REFERENCES auth_tenants (id);
ALTER TABLE auth_users DROP CONSTRAINT uq_auth_users_username;
ALTER TABLE auth_users DROP CONSTRAINT uq_auth_users_email;
ALTER TABLE auth_users ADD CONSTRAINT uq_auth_users_username UNIQUE (tenant_id, username);
ALTER TABLE auth_users ADD CONSTRAINT uq_auth_users_email UNIQUE (tenant_id, email);

-- Roles: the JPA element collection inserts only (user_id, role), so a trigger copies the
-- user's tenant onto each role row.
ALTER TABLE auth_user_roles ADD COLUMN tenant_id uuid;
UPDATE auth_user_roles r SET tenant_id = u.tenant_id FROM auth_users u WHERE u.id = r.user_id;
ALTER TABLE auth_user_roles ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX ix_auth_user_roles_tenant ON auth_user_roles (tenant_id);

CREATE FUNCTION auth_user_roles_set_tenant() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    SELECT u.tenant_id INTO NEW.tenant_id FROM auth_users u WHERE u.id = NEW.user_id;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_auth_user_roles_tenant
    BEFORE INSERT OR UPDATE OF user_id ON auth_user_roles
    FOR EACH ROW EXECUTE FUNCTION auth_user_roles_set_tenant();

-- Refresh tokens: same workshop as their user.
ALTER TABLE auth_refresh_tokens ADD COLUMN tenant_id uuid;
UPDATE auth_refresh_tokens t SET tenant_id = u.tenant_id FROM auth_users u WHERE u.id = t.user_id;
ALTER TABLE auth_refresh_tokens ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX ix_auth_refresh_tokens_tenant ON auth_refresh_tokens (tenant_id);
