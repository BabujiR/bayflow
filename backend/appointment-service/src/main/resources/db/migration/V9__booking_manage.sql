-- Online booking, step 3: customers change or cancel their own booking from the e-mailed link.
--
-- manage_token_hash: SHA-256 (hex) of the private link's token; the token itself is never stored.
-- customer_reschedules: times the customer moved it themselves (they may do so 3 times).
-- cancelled_by: CUSTOMER when they cancelled from the link (else the shop did), with their reason.
ALTER TABLE apt_appointments
    ADD COLUMN manage_token_hash      varchar(64),
    ADD COLUMN customer_reschedules   integer NOT NULL DEFAULT 0,
    ADD COLUMN customer_changed_at    timestamptz,
    ADD COLUMN cancelled_by           varchar(10),
    ADD COLUMN cancel_reason          varchar(300),
    ADD CONSTRAINT ck_apt_appointments_cancelled_by CHECK (cancelled_by IN ('CUSTOMER', 'STAFF')),
    ADD CONSTRAINT ck_apt_appointments_customer_reschedules CHECK (customer_reschedules >= 0);

CREATE UNIQUE INDEX ux_apt_appointments_manage_token ON apt_appointments (manage_token_hash)
    WHERE manage_token_hash IS NOT NULL;
