-- Organizations and locations, phase L1: a job card (and its service-history entry) belongs to
-- the location where the work is done, copied from the appointment. Existing rows belong to the
-- organization's first location, "Main", whose id is the tenant id (auth-service V7), so no
-- auth table is read here.

ALTER TABLE ws_job_cards ADD COLUMN location_id uuid;
UPDATE ws_job_cards SET location_id = tenant_id;
ALTER TABLE ws_job_cards ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_job_cards ADD CONSTRAINT ck_ws_job_cards_location
    CHECK (location_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
CREATE INDEX ix_ws_job_cards_location ON ws_job_cards (tenant_id, location_id, status);

ALTER TABLE ws_service_history ADD COLUMN location_id uuid;
UPDATE ws_service_history SET location_id = tenant_id;
ALTER TABLE ws_service_history ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_service_history ADD CONSTRAINT ck_ws_service_history_location
    CHECK (location_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
CREATE INDEX ix_ws_service_history_location ON ws_service_history (tenant_id, location_id, service_date);
