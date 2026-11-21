-- Account recovery. Staff can reset someone's forgotten password: the account gets a temporary
-- password that the person must replace at their next sign-in, and that stops working after a
-- while (bayflow.auth.temporary-password-ttl, 72 hours by default).
ALTER TABLE auth_users
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false,
    ADD COLUMN temporary_password_expires_at timestamptz;
