-- Onboarding step 1: the Ramplight console. Ramplight's own team signs in at
-- <base domain>/console as PLATFORM_ADMIN users of a reserved organization that has no workshop
-- address (RequestTenants refuses its slug), and works through the demo requests.

INSERT INTO auth_tenants (id, slug, name, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000c0', 'ramplight-console', 'Ramplight console', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

ALTER TABLE auth_user_roles DROP CONSTRAINT ck_auth_user_roles_role;
ALTER TABLE auth_user_roles
    ADD CONSTRAINT ck_auth_user_roles_role
        CHECK (role IN ('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN', 'PLATFORM_ADMIN'));

ALTER TABLE auth_demo_requests
    ADD COLUMN status     varchar(20)   NOT NULL DEFAULT 'NEW',
    ADD COLUMN notes      varchar(2000),
    ADD COLUMN updated_at timestamptz,
    ADD COLUMN updated_by varchar(100),
    ADD COLUMN version    bigint        NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_auth_demo_requests_status
        CHECK (status IN ('NEW', 'CONTACTED', 'DEMO_SCHEDULED', 'DEMO_DONE', 'INVITED', 'ONBOARDED', 'DECLINED'));

CREATE INDEX ix_auth_demo_requests_status ON auth_demo_requests (status, created_at DESC);

GRANT UPDATE ON auth_demo_requests TO ${app_role};
