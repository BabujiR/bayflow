-- Reports, phase R1: who sent each estimate to the customer (service-advisor report), and
-- indexes for the period queries of the owner dashboard and reports.

ALTER TABLE ws_job_cards ADD COLUMN submitted_by varchar(100);

-- Sales by period: completed cards by completion time.
CREATE INDEX ix_ws_job_cards_completed ON ws_job_cards (tenant_id, completed_at)
    WHERE status = 'COMPLETED';
-- Service-advisor report: cards by the time the estimate was sent.
CREATE INDEX ix_ws_job_cards_submitted ON ws_job_cards (tenant_id, submitted_at)
    WHERE submitted_at IS NOT NULL;
-- Technician hours and parts usage by the time they were recorded.
CREATE INDEX ix_ws_labour_entries_created ON ws_labour_entries (tenant_id, created_at);
CREATE INDEX ix_ws_part_usages_created ON ws_part_usages (tenant_id, created_at);
-- Approved and declined work by decision time.
CREATE INDEX ix_ws_items_decided ON ws_recommended_items (tenant_id, decided_at)
    WHERE decided_at IS NOT NULL;
