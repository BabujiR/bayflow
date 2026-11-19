-- Multi-tenancy guard: a row must always belong to a real workshop. Refuses the two reserved
-- ids: the "no workshop known" id (a session opened without a tenant) and the root id (a
-- cross-workshop session). Without this, such a session could store a row no workshop owns.

ALTER TABLE ws_job_cards ADD CONSTRAINT ck_ws_job_cards_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_inspection_findings ADD CONSTRAINT ck_ws_inspection_findings_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_recommended_items ADD CONSTRAINT ck_ws_recommended_items_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_work_logs ADD CONSTRAINT ck_ws_work_logs_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_part_usages ADD CONSTRAINT ck_ws_part_usages_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_labour_entries ADD CONSTRAINT ck_ws_labour_entries_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
ALTER TABLE ws_service_history ADD CONSTRAINT ck_ws_service_history_tenant CHECK (tenant_id NOT IN ('00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff'));
