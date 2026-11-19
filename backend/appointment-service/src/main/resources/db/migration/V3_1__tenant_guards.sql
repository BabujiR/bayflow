-- Multi-tenancy guard: a row must always belong to a real workshop. Refuses the two reserved
-- ids: the "no workshop known" id (a session opened without a tenant) and the root id (a
-- cross-workshop session). Without this, such a session could store a row no workshop owns.

ALTER TABLE apt_customers ADD CONSTRAINT ck_apt_customers_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE apt_vehicles ADD CONSTRAINT ck_apt_vehicles_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE apt_appointments ADD CONSTRAINT ck_apt_appointments_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE apt_outbox ADD CONSTRAINT ck_apt_outbox_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
