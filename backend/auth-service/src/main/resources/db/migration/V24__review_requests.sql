-- Google review requests: per location, the shop's review link, whether to ask, and when.
ALTER TABLE auth_locations
    ADD COLUMN review_url varchar(500),
    ADD COLUMN review_requests boolean NOT NULL DEFAULT false,
    ADD COLUMN review_delay varchar(20) NOT NULL DEFAULT 'NEXT_MORNING';

ALTER TABLE auth_locations
    ADD CONSTRAINT ck_auth_locations_review_delay CHECK (review_delay IN ('TWO_HOURS', 'NEXT_MORNING', 'TWO_DAYS')),
    -- Asking needs somewhere to send the customer.
    ADD CONSTRAINT ck_auth_locations_review_url CHECK (NOT review_requests OR review_url IS NOT NULL);
