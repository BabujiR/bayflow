-- Multi-tenancy, phase 1: every job card, its details and every service-history entry belong
-- to a workshop (tenant). Existing rows move into the "default" workshop (Flyway placeholder
-- default_tenant_id, the same value in every service). Child tables carry their own
-- tenant_id so later row-level security needs no joins.

ALTER TABLE ws_job_cards           ADD COLUMN tenant_id uuid;
ALTER TABLE ws_inspection_findings ADD COLUMN tenant_id uuid;
ALTER TABLE ws_recommended_items   ADD COLUMN tenant_id uuid;
ALTER TABLE ws_work_logs           ADD COLUMN tenant_id uuid;
ALTER TABLE ws_part_usages         ADD COLUMN tenant_id uuid;
ALTER TABLE ws_labour_entries      ADD COLUMN tenant_id uuid;
ALTER TABLE ws_service_history     ADD COLUMN tenant_id uuid;

UPDATE ws_job_cards           SET tenant_id = '${default_tenant_id}';
UPDATE ws_inspection_findings SET tenant_id = '${default_tenant_id}';
UPDATE ws_recommended_items   SET tenant_id = '${default_tenant_id}';
UPDATE ws_work_logs           SET tenant_id = '${default_tenant_id}';
UPDATE ws_part_usages         SET tenant_id = '${default_tenant_id}';
UPDATE ws_labour_entries      SET tenant_id = '${default_tenant_id}';
UPDATE ws_service_history     SET tenant_id = '${default_tenant_id}';

ALTER TABLE ws_job_cards           ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_inspection_findings ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_recommended_items   ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_work_logs           ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_part_usages         ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_labour_entries      ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ws_service_history     ALTER COLUMN tenant_id SET NOT NULL;

-- Lists filter by tenant first. Child rows are always reached through their job card.
CREATE INDEX ix_ws_job_cards_tenant_status ON ws_job_cards (tenant_id, status);
CREATE INDEX ix_ws_service_history_tenant_vehicle ON ws_service_history (tenant_id, vehicle_id, service_date DESC);
