-- Organizations and locations, phase L2: staff work at particular locations.
-- MANAGER is a location's general manager (a regional manager is a MANAGER of several).
-- MANAGER, SERVICE_ADVISOR and MECHANIC accounts are assigned to one or more locations;
-- ADMIN and CUSTOMER accounts cover every location of their organization.

ALTER TABLE auth_user_roles DROP CONSTRAINT ck_auth_user_roles_role;
ALTER TABLE auth_user_roles
    ADD CONSTRAINT ck_auth_user_roles_role
        CHECK (role IN ('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN'));

CREATE TABLE auth_user_locations (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL,
    user_id     uuid        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    location_id uuid        NOT NULL REFERENCES auth_locations (id),
    created_at  timestamptz NOT NULL,
    CONSTRAINT uq_auth_user_locations UNIQUE (user_id, location_id),
    CONSTRAINT ck_auth_user_locations_tenant
        CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'))
);

CREATE INDEX ix_auth_user_locations_user ON auth_user_locations (tenant_id, user_id);

-- Every organization has one location so far ("Main", id = tenant id): its existing advisors
-- and mechanics work there.
INSERT INTO auth_user_locations (id, tenant_id, user_id, location_id, created_at)
SELECT DISTINCT ON (r.user_id) gen_random_uuid(), r.tenant_id, r.user_id, r.tenant_id, now()
FROM auth_user_roles r
JOIN auth_locations l ON l.id = r.tenant_id
WHERE r.role IN ('SERVICE_ADVISOR', 'MECHANIC');

GRANT SELECT, INSERT, DELETE ON auth_user_locations TO ${app_role};

ALTER TABLE auth_user_locations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_user_locations
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid);
