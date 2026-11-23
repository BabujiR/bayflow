-- R2b: invoices e-mailed to the customer (the latest send is kept; auth-service holds the e-mail itself).
ALTER TABLE ws_invoices
    ADD COLUMN emailed_at timestamptz,
    ADD COLUMN emailed_to varchar(254),
    ADD COLUMN emailed_by varchar(100);
