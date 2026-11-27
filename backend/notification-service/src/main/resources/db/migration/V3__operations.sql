-- One row per scheduled job: which instance runs it, and until when at most (JobLock). With two
-- or more instances of the service, each job runs on one of them at a time. No tenant: the jobs
-- work across workshops.
CREATE TABLE ntf_job_locks (
    name         varchar(100) PRIMARY KEY,
    locked_until timestamptz  NOT NULL,
    locked_by    varchar(200) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON ntf_job_locks TO ${app_role};
