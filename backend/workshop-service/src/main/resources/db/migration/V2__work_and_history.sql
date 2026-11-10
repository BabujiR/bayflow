-- Work, completion and service history (milestone 5).

ALTER TABLE ws_job_cards
    ADD COLUMN started_at   timestamptz,
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN parts_total  numeric(12,2),
    ADD COLUMN labour_total numeric(12,2),
    ADD COLUMN grand_total  numeric(12,2);

CREATE TABLE ws_work_logs (
    id                uuid          PRIMARY KEY,
    job_card_id       uuid          NOT NULL REFERENCES ws_job_cards (id) ON DELETE CASCADE,
    recommendation_id uuid          NOT NULL REFERENCES ws_recommended_items (id),
    mechanic          varchar(100)  NOT NULL,
    description       varchar(1000) NOT NULL,
    completed_at      timestamptz   NOT NULL
);

CREATE INDEX ix_ws_work_logs_job_card ON ws_work_logs (job_card_id);

CREATE TABLE ws_part_usages (
    id                uuid          PRIMARY KEY,
    job_card_id       uuid          NOT NULL REFERENCES ws_job_cards (id) ON DELETE CASCADE,
    recommendation_id uuid          NOT NULL REFERENCES ws_recommended_items (id),
    part_number       varchar(50)   NOT NULL,
    name              varchar(200)  NOT NULL,
    quantity          integer       NOT NULL,
    unit_price        numeric(12,2) NOT NULL,
    recorded_by       varchar(100)  NOT NULL,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT ck_ws_parts_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_ws_parts_price CHECK (unit_price >= 0)
);

CREATE INDEX ix_ws_part_usages_job_card ON ws_part_usages (job_card_id);

CREATE TABLE ws_labour_entries (
    id                uuid          PRIMARY KEY,
    job_card_id       uuid          NOT NULL REFERENCES ws_job_cards (id) ON DELETE CASCADE,
    recommendation_id uuid          NOT NULL REFERENCES ws_recommended_items (id),
    mechanic          varchar(100)  NOT NULL,
    hours             numeric(6,2)  NOT NULL,
    hourly_rate       numeric(10,2) NOT NULL,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT ck_ws_labour_hours CHECK (hours > 0),
    CONSTRAINT ck_ws_labour_rate CHECK (hourly_rate >= 0)
);

CREATE INDEX ix_ws_labour_entries_job_card ON ws_labour_entries (job_card_id);

-- Written once per completed job card; never updated.
CREATE TABLE ws_service_history (
    id               uuid          PRIMARY KEY,
    vehicle_id       uuid          NOT NULL,
    job_card_id      uuid          NOT NULL REFERENCES ws_job_cards (id),
    appointment_id   uuid          NOT NULL,
    customer_user_id uuid,
    vin              varchar(17)   NOT NULL,
    registration     varchar(20)   NOT NULL,
    service_date     timestamptz   NOT NULL,
    mileage          integer       NOT NULL,
    details          text          NOT NULL,
    parts_total      numeric(12,2) NOT NULL,
    labour_total     numeric(12,2) NOT NULL,
    total_cost       numeric(12,2) NOT NULL,
    created_at       timestamptz   NOT NULL,
    CONSTRAINT uq_ws_service_history_job_card UNIQUE (job_card_id)
);

CREATE INDEX ix_ws_service_history_vehicle ON ws_service_history (vehicle_id, service_date DESC);
