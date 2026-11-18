-- Organizations and locations, phase L1: every appointment happens at one location (shop) of
-- its organization. Existing appointments belong to the organization's first location, "Main",
-- whose id is the tenant id (auth-service V7), so no auth table is read here.
-- Customers and vehicles stay organization-wide: a customer can visit any location.

ALTER TABLE apt_appointments ADD COLUMN location_id uuid;
UPDATE apt_appointments SET location_id = tenant_id;
ALTER TABLE apt_appointments ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE apt_appointments ADD CONSTRAINT ck_apt_appointments_location
    CHECK (location_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
CREATE INDEX ix_apt_appointments_location ON apt_appointments (tenant_id, location_id, scheduled_at);
