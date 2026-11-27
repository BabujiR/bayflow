-- One row per scheduled job: which instance runs it, and until when at most (JobLock). With two
-- or more instances of the service, each job runs on one of them at a time. No tenant: the jobs
-- work across workshops.
CREATE TABLE auth_job_locks (
    name         varchar(100) PRIMARY KEY,
    locked_until timestamptz  NOT NULL,
    locked_by    varchar(200) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON auth_job_locks TO ${app_role};

-- Rate-limit counts, shared by every instance of the service (JdbcRateLimitStore): hits per
-- bucket (a limit and its key, such as a login's address) in a fixed window. Old windows are
-- deleted as new ones are counted.
CREATE TABLE auth_rate_limits (
    bucket       varchar(200) NOT NULL,
    window_start timestamptz  NOT NULL,
    hits         integer      NOT NULL,
    PRIMARY KEY (bucket, window_start)
);

CREATE INDEX ix_auth_rate_limits_window ON auth_rate_limits (window_start);

GRANT SELECT, INSERT, UPDATE, DELETE ON auth_rate_limits TO ${app_role};
