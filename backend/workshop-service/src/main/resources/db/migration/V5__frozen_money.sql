-- Multi-tenancy, phase 3: money is frozen on the job card. Each workshop now sets its own
-- labour rate and currency (auth-service); a card copies them when it opens, so a later
-- settings change never rewrites an open estimate or a finished job's history.
-- Existing cards and history get the values configured until now.

ALTER TABLE ws_job_cards ADD COLUMN labour_rate numeric(10,2);
ALTER TABLE ws_job_cards ADD COLUMN currency varchar(3);
UPDATE ws_job_cards SET labour_rate = ${default_labour_rate}, currency = '${default_currency}';
ALTER TABLE ws_job_cards ALTER COLUMN labour_rate SET NOT NULL;
ALTER TABLE ws_job_cards ALTER COLUMN currency SET NOT NULL;
ALTER TABLE ws_job_cards ADD CONSTRAINT ck_ws_job_cards_labour_rate CHECK (labour_rate >= 0);
ALTER TABLE ws_job_cards ADD CONSTRAINT ck_ws_job_cards_currency CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE ws_service_history ADD COLUMN currency varchar(3);
UPDATE ws_service_history SET currency = '${default_currency}';
ALTER TABLE ws_service_history ALTER COLUMN currency SET NOT NULL;
