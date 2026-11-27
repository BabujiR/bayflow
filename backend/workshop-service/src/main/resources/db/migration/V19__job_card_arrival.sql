-- When the car reached the shop (its check-in). A quick estimate's job card opens before that:
-- arrived_at stays empty until the visit is checked in, and work can't start without it.
ALTER TABLE ws_job_cards ADD COLUMN arrived_at timestamptz;

-- Every existing card was opened at check-in.
UPDATE ws_job_cards SET arrived_at = created_at;

CREATE INDEX ix_ws_job_cards_not_arrived ON ws_job_cards (created_at) WHERE arrived_at IS NULL;
