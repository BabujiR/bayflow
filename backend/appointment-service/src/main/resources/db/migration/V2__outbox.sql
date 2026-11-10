-- Transactional outbox: requests to workshop-service saved together with the check-in.

CREATE TABLE apt_outbox (
    id              uuid          PRIMARY KEY,
    appointment_id  uuid          NOT NULL REFERENCES apt_appointments (id),
    type            varchar(50)   NOT NULL,
    payload         text          NOT NULL,
    attempts        integer       NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    last_error      varchar(1000),
    processed_at    timestamptz,
    created_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);

-- The dispatcher only ever looks at undelivered messages.
CREATE INDEX ix_apt_outbox_pending ON apt_outbox (next_attempt_at) WHERE processed_at IS NULL;
CREATE INDEX ix_apt_outbox_appointment ON apt_outbox (appointment_id);
