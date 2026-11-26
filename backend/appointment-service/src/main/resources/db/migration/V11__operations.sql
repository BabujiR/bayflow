-- One row per scheduled job: which instance runs it, and until when at most (JobLock). With two
-- or more instances of the service, each job runs on one of them at a time. No tenant: the jobs
-- work across workshops.
CREATE TABLE apt_job_locks (
    name         varchar(100) PRIMARY KEY,
    locked_until timestamptz  NOT NULL,
    locked_by    varchar(200) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON apt_job_locks TO ${app_role};

-- Rate-limit counts, shared by every instance of the service (JdbcRateLimitStore): hits per
-- bucket (a limit and its key, such as a login's address) in a fixed window. Old windows are
-- deleted as new ones are counted.
CREATE TABLE apt_rate_limits (
    bucket       varchar(200) NOT NULL,
    window_start timestamptz  NOT NULL,
    hits         integer      NOT NULL,
    PRIMARY KEY (bucket, window_start)
);

CREATE INDEX ix_apt_rate_limits_window ON apt_rate_limits (window_start);

GRANT SELECT, INSERT, UPDATE, DELETE ON apt_rate_limits TO ${app_role};

-- Outbox numbers for the metrics (bayflow.outbox.*), across every workshop: row-level security
-- would show none, so this runs as the owner and returns only counts.
CREATE FUNCTION apt_outbox_stats()
    RETURNS TABLE (pending bigint, failed bigint, oldest_pending_seconds double precision)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT count(*) FILTER (WHERE o.failed_at IS NULL),
           count(*) FILTER (WHERE o.failed_at IS NOT NULL),
           coalesce(extract(epoch FROM now() - min(o.created_at) FILTER (WHERE o.failed_at IS NULL)), 0)::float8
    FROM apt_outbox o
    WHERE o.processed_at IS NULL
$$;

REVOKE ALL ON FUNCTION apt_outbox_stats() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION apt_outbox_stats() TO ${app_role};
