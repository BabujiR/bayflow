-- "Request a demo" from someone whose earlier request is still open (New, Contacted, Demo
-- scheduled) no longer makes a new row: the new details go into that request's notes, and the
-- console shows it as a repeat, moved back to the top.
ALTER TABLE auth_demo_requests
    ADD COLUMN repeat_count      integer     NOT NULL DEFAULT 0,
    ADD COLUMN last_requested_at timestamptz;

CREATE INDEX ix_auth_demo_requests_email ON auth_demo_requests (email, created_at DESC);
