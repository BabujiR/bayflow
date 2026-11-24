-- "Request a demo" no longer asks for the shop type: new requests have none. Older ones keep theirs.
ALTER TABLE auth_demo_requests ALTER COLUMN shop_type DROP NOT NULL;
