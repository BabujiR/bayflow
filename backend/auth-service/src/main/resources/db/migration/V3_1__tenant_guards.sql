-- Multi-tenancy guard: a row must always belong to a real workshop. Refuses the two reserved
-- ids: the "no workshop known" id (a session opened without a tenant) and the root id (a
-- cross-workshop session). Without this, such a session could store a row no workshop owns.

ALTER TABLE auth_users ADD CONSTRAINT ck_auth_users_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE auth_user_roles ADD CONSTRAINT ck_auth_user_roles_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE auth_refresh_tokens ADD CONSTRAINT ck_auth_refresh_tokens_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
