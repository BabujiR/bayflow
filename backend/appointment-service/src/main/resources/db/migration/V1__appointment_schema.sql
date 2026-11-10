-- appointment-service tables (prefix apt_). Owned only by appointment-service.

-- Needed for the exclusion constraint that blocks overlapping bookings per vehicle.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE apt_customers (
    id         uuid         PRIMARY KEY,
    user_id    uuid,
    name       varchar(100) NOT NULL,
    email      varchar(254) NOT NULL,
    phone      varchar(30),
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_apt_customers_user UNIQUE (user_id)
);

CREATE INDEX ix_apt_customers_name ON apt_customers (lower(name));

CREATE TABLE apt_vehicles (
    id           uuid        PRIMARY KEY,
    customer_id  uuid        NOT NULL REFERENCES apt_customers (id),
    vin          varchar(17) NOT NULL,
    registration varchar(20) NOT NULL,
    make         varchar(50) NOT NULL,
    model        varchar(50) NOT NULL,
    model_year   integer     NOT NULL,
    mileage      integer     NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_apt_vehicles_vin UNIQUE (vin),
    CONSTRAINT ck_apt_vehicles_mileage CHECK (mileage >= 0)
);

CREATE INDEX ix_apt_vehicles_customer ON apt_vehicles (customer_id);

CREATE TABLE apt_appointments (
    id               uuid          PRIMARY KEY,
    customer_id      uuid          NOT NULL REFERENCES apt_customers (id),
    vehicle_id       uuid          NOT NULL REFERENCES apt_vehicles (id),
    scheduled_at     timestamptz   NOT NULL,
    ends_at          timestamptz   NOT NULL,
    duration_minutes integer       NOT NULL,
    service_type     varchar(30)   NOT NULL,
    notes            varchar(1000),
    status           varchar(20)   NOT NULL,
    arrival_mileage  integer,
    confirmed_at     timestamptz,
    arrived_at       timestamptz,
    checked_in_at    timestamptz,
    cancelled_at     timestamptz,
    job_card_id      uuid,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_apt_appointments_status
        CHECK (status IN ('BOOKED', 'CONFIRMED', 'ARRIVED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_apt_appointments_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_apt_appointments_slot CHECK (ends_at > scheduled_at),
    -- Last line of defence against double booking under concurrent requests:
    -- active appointments of the same vehicle may not overlap in time.
    CONSTRAINT ex_apt_appointments_no_overlap EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(scheduled_at, ends_at) WITH &&
    ) WHERE (status IN ('BOOKED', 'CONFIRMED', 'ARRIVED'))
);

CREATE INDEX ix_apt_appointments_scheduled ON apt_appointments (scheduled_at);
CREATE INDEX ix_apt_appointments_status ON apt_appointments (status);
CREATE INDEX ix_apt_appointments_customer ON apt_appointments (customer_id);
CREATE INDEX ix_apt_appointments_vehicle ON apt_appointments (vehicle_id);
