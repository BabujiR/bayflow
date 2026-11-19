-- Organizations and locations, phase L2: staff see and change only the job cards (and their
-- findings, recommendations, work, parts and labour) and the service history of the locations
-- they work at. Each transaction names them in app.locations ("*" = every location of the
-- organization, otherwise a comma-separated list of ids; missing = none).
--
-- Like tenant_id, every table carries location_id itself, so each policy checks its own rows
-- without joins. A composite foreign key keeps a child's location equal to its card's.

ALTER TABLE ws_job_cards ADD CONSTRAINT uq_ws_job_cards_id_location UNIQUE (id, location_id);

ALTER TABLE ws_inspection_findings ADD COLUMN location_id uuid;
UPDATE ws_inspection_findings c SET location_id = j.location_id FROM ws_job_cards j WHERE j.id = c.job_card_id;
ALTER TABLE ws_inspection_findings ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_inspection_findings ADD CONSTRAINT fk_inspection_findings_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id) ON DELETE CASCADE;

ALTER TABLE ws_recommended_items ADD COLUMN location_id uuid;
UPDATE ws_recommended_items c SET location_id = j.location_id FROM ws_job_cards j WHERE j.id = c.job_card_id;
ALTER TABLE ws_recommended_items ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_recommended_items ADD CONSTRAINT fk_recommended_items_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id) ON DELETE CASCADE;

ALTER TABLE ws_work_logs ADD COLUMN location_id uuid;
UPDATE ws_work_logs c SET location_id = j.location_id FROM ws_job_cards j WHERE j.id = c.job_card_id;
ALTER TABLE ws_work_logs ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_work_logs ADD CONSTRAINT fk_work_logs_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id) ON DELETE CASCADE;

ALTER TABLE ws_part_usages ADD COLUMN location_id uuid;
UPDATE ws_part_usages c SET location_id = j.location_id FROM ws_job_cards j WHERE j.id = c.job_card_id;
ALTER TABLE ws_part_usages ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_part_usages ADD CONSTRAINT fk_part_usages_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id) ON DELETE CASCADE;

ALTER TABLE ws_labour_entries ADD COLUMN location_id uuid;
UPDATE ws_labour_entries c SET location_id = j.location_id FROM ws_job_cards j WHERE j.id = c.job_card_id;
ALTER TABLE ws_labour_entries ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE ws_labour_entries ADD CONSTRAINT fk_labour_entries_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id) ON DELETE CASCADE;

-- History entries copy their card's location the same way.
ALTER TABLE ws_service_history ADD CONSTRAINT fk_service_history_card_location
    FOREIGN KEY (job_card_id, location_id) REFERENCES ws_job_cards (id, location_id);

DROP POLICY tenant_isolation ON ws_job_cards;
CREATE POLICY tenant_location_isolation ON ws_job_cards
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_inspection_findings;
CREATE POLICY tenant_location_isolation ON ws_inspection_findings
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_recommended_items;
CREATE POLICY tenant_location_isolation ON ws_recommended_items
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_work_logs;
CREATE POLICY tenant_location_isolation ON ws_work_logs
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_part_usages;
CREATE POLICY tenant_location_isolation ON ws_part_usages
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_labour_entries;
CREATE POLICY tenant_location_isolation ON ws_labour_entries
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);

DROP POLICY tenant_isolation ON ws_service_history;
CREATE POLICY tenant_location_isolation ON ws_service_history
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);
