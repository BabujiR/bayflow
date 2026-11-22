-- Invoicing (R2a): each location's tax rates, shop fees and invoice terms. Rates are percentages
-- (8.875 = 8.875 %). Fees are off while null. Copied onto each invoice when it is created, so a
-- later change never alters old invoices.

ALTER TABLE auth_locations
    ADD COLUMN parts_tax_rate        numeric(6,3)  NOT NULL DEFAULT 0,
    ADD COLUMN labour_tax_rate       numeric(6,3)  NOT NULL DEFAULT 0,
    ADD COLUMN fees_tax_rate         numeric(6,3)  NOT NULL DEFAULT 0,
    ADD COLUMN shop_supplies_percent numeric(6,3),
    ADD COLUMN shop_supplies_cap     numeric(10,2),
    ADD COLUMN disposal_fee          numeric(10,2),
    ADD COLUMN payment_terms_days    integer       NOT NULL DEFAULT 0,
    ADD COLUMN invoice_footer        varchar(500),
    ADD CONSTRAINT ck_auth_locations_tax_rates CHECK (
        parts_tax_rate BETWEEN 0 AND 100 AND labour_tax_rate BETWEEN 0 AND 100 AND fees_tax_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_auth_locations_fees CHECK (
        (shop_supplies_percent IS NULL OR shop_supplies_percent BETWEEN 0 AND 100)
        AND (shop_supplies_cap IS NULL OR shop_supplies_cap >= 0)
        AND (disposal_fee IS NULL OR disposal_fee >= 0)),
    ADD CONSTRAINT ck_auth_locations_terms CHECK (payment_terms_days BETWEEN 0 AND 365);
