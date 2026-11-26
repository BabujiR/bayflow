-- Avatars: each user shows as their initials (the default), one of the built-in characters, or
-- their own photo. The choice is on the user; a photo's bytes (already checked, cut square and
-- re-encoded as a 256 px JPEG) live in auth_user_avatars, one row per user.
ALTER TABLE auth_users
    ADD COLUMN avatar_kind      varchar(10) NOT NULL DEFAULT 'INITIALS',
    ADD COLUMN avatar_character varchar(20),
    ADD COLUMN avatar_sha256    varchar(64),
    ADD CONSTRAINT ck_auth_users_avatar_kind CHECK (avatar_kind IN ('INITIALS', 'CHARACTER', 'PHOTO')),
    ADD CONSTRAINT ck_auth_users_avatar_character
        CHECK ((avatar_kind = 'CHARACTER') = (avatar_character IS NOT NULL)),
    ADD CONSTRAINT ck_auth_users_avatar_photo CHECK ((avatar_kind = 'PHOTO') = (avatar_sha256 IS NOT NULL));

CREATE TABLE auth_user_avatars (
    user_id      uuid        PRIMARY KEY REFERENCES auth_users (id) ON DELETE CASCADE,
    tenant_id    uuid        NOT NULL REFERENCES auth_tenants (id),
    content_type varchar(20) NOT NULL,
    content      bytea       NOT NULL,
    -- SHA-256 of the content: the ETag and the cache-busting part of the photo's address.
    sha256       varchar(64) NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT ck_auth_user_avatars_type CHECK (content_type = 'image/jpeg'),
    CONSTRAINT ck_auth_user_avatars_size CHECK (octet_length(content) <= 262144)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON auth_user_avatars TO ${app_role};

ALTER TABLE auth_user_avatars ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_user_avatars
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
