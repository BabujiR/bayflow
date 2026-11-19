-- Multi-tenancy, phase 1: every customer, vehicle, appointment and outbox message belongs to
-- a workshop (tenant). Existing rows move into the "default" workshop (Flyway placeholder
-- default_tenant_id, the same value in every service). Tenants themselves live in
-- auth-service; there is no cross-service foreign key, as for every other reference.

ALTER TABLE apt_customers    ADD COLUMN tenant_id uuid;
ALTER TABLE apt_vehicles     ADD COLUMN tenant_id uuid;
ALTER TABLE apt_appointments ADD COLUMN tenant_id uuid;
ALTER TABLE apt_outbox       ADD COLUMN tenant_id uuid;

UPDATE apt_customers    SET tenant_id = '${default_tenant_id}';
UPDATE apt_vehicles     SET tenant_id = '${default_tenant_id}';
UPDATE apt_appointments SET tenant_id = '${default_tenant_id}';
UPDATE apt_outbox       SET tenant_id = '${default_tenant_id}';

ALTER TABLE apt_customers    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE apt_vehicles     ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE apt_appointments ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE apt_outbox       ALTER COLUMN tenant_id SET NOT NULL;

-- The same car may be serviced by two different workshops: VIN is unique per workshop.
ALTER TABLE apt_vehicles DROP CONSTRAINT uq_apt_vehicles_vin;
ALTER TABLE apt_vehicles ADD CONSTRAINT uq_apt_vehicles_vin UNIQUE (tenant_id, vin);

-- Every query filters by tenant first.
CREATE INDEX ix_apt_customers_tenant_name ON apt_customers (tenant_id, lower(name));
CREATE INDEX ix_apt_vehicles_tenant_customer ON apt_vehicles (tenant_id, customer_id);
CREATE INDEX ix_apt_appointments_tenant_scheduled ON apt_appointments (tenant_id, scheduled_at);
CREATE INDEX ix_apt_outbox_tenant ON apt_outbox (tenant_id);
