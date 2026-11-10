-- workshop-service tables (prefix ws_). Owned only by workshop-service.

CREATE TABLE ws_job_cards (
    id               uuid         PRIMARY KEY,
    appointment_id   uuid         NOT NULL,
    vehicle_id       uuid         NOT NULL,
    vin              varchar(17)  NOT NULL,
    registration     varchar(20)  NOT NULL,
    customer_user_id uuid,
    customer_name    varchar(100) NOT NULL,
    mileage          integer      NOT NULL,
    status           varchar(30)  NOT NULL,
    submitted_at     timestamptz,
    decided_at       timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    -- One job card per appointment: makes job-card creation idempotent.
    CONSTRAINT uq_ws_job_cards_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_ws_job_cards_mileage CHECK (mileage >= 0),
    CONSTRAINT ck_ws_job_cards_status CHECK (status IN ('OPEN', 'INSPECTED', 'AWAITING_APPROVAL', 'APPROVED',
        'PARTIALLY_APPROVED', 'DECLINED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX ix_ws_job_cards_status ON ws_job_cards (status);
CREATE INDEX ix_ws_job_cards_vehicle ON ws_job_cards (vehicle_id);
CREATE INDEX ix_ws_job_cards_customer ON ws_job_cards (customer_user_id);

CREATE TABLE ws_inspection_findings (
    id          uuid          PRIMARY KEY,
    job_card_id uuid          NOT NULL REFERENCES ws_job_cards (id) ON DELETE CASCADE,
    description varchar(1000) NOT NULL,
    severity    varchar(20)   NOT NULL,
    recorded_by varchar(100)  NOT NULL,
    created_at  timestamptz   NOT NULL,
    CONSTRAINT ck_ws_findings_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_ws_findings_job_card ON ws_inspection_findings (job_card_id);

CREATE TABLE ws_recommended_items (
    id                     uuid          PRIMARY KEY,
    job_card_id            uuid          NOT NULL REFERENCES ws_job_cards (id) ON DELETE CASCADE,
    description            varchar(1000) NOT NULL,
    estimated_parts_cost   numeric(12,2) NOT NULL,
    estimated_labour_hours numeric(6,2)  NOT NULL,
    approval_status        varchar(20)   NOT NULL,
    decided_by             varchar(150),
    decided_at             timestamptz,
    created_at             timestamptz   NOT NULL,
    CONSTRAINT ck_ws_items_estimates CHECK (estimated_parts_cost >= 0 AND estimated_labour_hours >= 0),
    CONSTRAINT ck_ws_items_approval CHECK (approval_status IN ('PENDING', 'APPROVED', 'DECLINED'))
);

CREATE INDEX ix_ws_items_job_card ON ws_recommended_items (job_card_id);
