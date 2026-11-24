-- Demo requests from the public site (getramplight.com): platform-level, not a workshop's data, so
-- no tenant_id and no row-level security. Personal data: kept for follow-up only.
CREATE TABLE auth_demo_requests (
    id            uuid          PRIMARY KEY,
    first_name    varchar(60)   NOT NULL,
    last_name     varchar(60)   NOT NULL,
    email         varchar(254)  NOT NULL,
    phone         varchar(30)   NOT NULL,
    shop_name     varchar(120)  NOT NULL,
    shop_type     varchar(30)   NOT NULL,
    locations     varchar(10)   NOT NULL,
    city          varchar(120),
    message       varchar(1000),
    client_ip     varchar(64),
    created_at    timestamptz   NOT NULL
);

CREATE INDEX ix_auth_demo_requests_created ON auth_demo_requests (created_at DESC);

GRANT SELECT, INSERT ON auth_demo_requests TO ${app_role};
