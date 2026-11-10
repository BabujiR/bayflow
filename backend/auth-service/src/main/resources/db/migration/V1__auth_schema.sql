-- auth-service tables (prefix auth_). Owned only by auth-service.

CREATE TABLE auth_users (
    id              uuid         PRIMARY KEY,
    username        varchar(50)  NOT NULL,
    email           varchar(254) NOT NULL,
    password_hash   varchar(100) NOT NULL,
    enabled         boolean      NOT NULL DEFAULT true,
    failed_attempts integer      NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_users_username UNIQUE (username),
    CONSTRAINT uq_auth_users_email UNIQUE (email),
    CONSTRAINT ck_auth_users_username_lower CHECK (username = lower(username)),
    CONSTRAINT ck_auth_users_email_lower CHECK (email = lower(email))
);

CREATE TABLE auth_user_roles (
    user_id uuid        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    role    varchar(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_auth_user_roles_role CHECK (role IN ('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC'))
);

CREATE TABLE auth_refresh_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked     boolean     NOT NULL DEFAULT false,
    replaced_by uuid,
    created_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_auth_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_auth_refresh_tokens_family ON auth_refresh_tokens (family_id);
CREATE INDEX ix_auth_refresh_tokens_user ON auth_refresh_tokens (user_id);

CREATE TABLE auth_clients (
    client_id   varchar(100) PRIMARY KEY,
    secret_hash varchar(100) NOT NULL,
    roles       varchar(200) NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0
);
