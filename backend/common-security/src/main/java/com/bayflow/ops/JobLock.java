package com.bayflow.ops;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a scheduled job on one instance of a service at a time, through a lock row in
 * PostgreSQL (the service's {@code <prefix>_job_locks} table, schema-qualified): with two or more instances, a
 * daily reminder is sent once, not once per instance. A lock is held at most {@code atMost}, so
 * a crashed instance can't block a job for good. Each statement commits on its own connection,
 * apart from any transaction the job opens.
 */
public class JobLock {

    private static final Logger log = LoggerFactory.getLogger(JobLock.class);
    private static final Pattern TABLE = Pattern.compile("([a-z_]{1,40}\\.)?[a-z_]{1,40}");

    private final DataSource dataSource;
    private final String table;
    /** This instance, for the lock row (and log lines): host/pid plus a random part. */
    private final String owner;

    public JobLock(DataSource dataSource, String table) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Bad lock table name: " + table);
        }
        this.dataSource = dataSource;
        this.table = table;
        this.owner = ManagementFactory.getRuntimeMXBean().getName() + "/"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Runs {@code work} if no other instance is running {@code job}; true when it ran here. A
     * failure to reach the database skips the run (the next one tries again).
     *
     * @param atLeast how long the lock stays taken even after a quick run: for a cron job, so an
     *            instance whose clock is a few seconds behind doesn't run it a second time
     * @param atMost how long the lock lasts if this instance dies mid-run
     */
    public boolean runExclusively(String job, Duration atLeast, Duration atMost, Runnable work) {
        if (!acquire(job, atMost)) {
            log.debug("Job {} is running elsewhere; skipped here", job);
            return false;
        }
        long started = System.nanoTime();
        try {
            work.run();
            return true;
        } finally {
            Duration left = atLeast.minus(Duration.ofNanos(System.nanoTime() - started));
            release(job, left.isNegative() ? Duration.ZERO : left);
        }
    }

    private boolean acquire(String job, Duration atMost) {
        String sql = "insert into " + table + " as l (name, locked_until, locked_by) "
                + "values (?, now() + make_interval(secs => ?), ?) "
                + "on conflict (name) do update "
                + "set locked_until = excluded.locked_until, locked_by = excluded.locked_by "
                + "where l.locked_until <= now()";
        try (Connection connection = autocommit(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job);
            statement.setDouble(2, atMost.toMillis() / 1000.0);
            statement.setString(3, owner);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            log.warn("Could not take the lock for job {}: {}", job, e.getMessage());
            return false;
        }
    }

    private void release(String job, Duration keep) {
        String sql = "update " + table + " set locked_until = now() + make_interval(secs => ?) "
                + "where name = ? and locked_by = ?";
        try (Connection connection = autocommit(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, keep.toMillis() / 1000.0);
            statement.setString(2, job);
            statement.setString(3, owner);
            statement.executeUpdate();
        } catch (SQLException e) {
            // It expires on its own (atMost).
            log.warn("Could not release the lock for job {}: {}", job, e.getMessage());
        }
    }

    private Connection autocommit() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(true);
        return connection;
    }
}
