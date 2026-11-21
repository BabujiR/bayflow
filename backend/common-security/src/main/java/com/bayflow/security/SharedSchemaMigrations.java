package com.bayflow.security;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * Runs a service's Flyway migrations while holding one database-wide lock that every service
 * takes, so their migrations never run at the same time.
 *
 * <p>All services keep their tables in the one schema and each grants the app role access to
 * it ({@code GRANT USAGE ON SCHEMA ...}). On a new database the services start together, and
 * two such statements at the same moment fail with "tuple concurrently updated" (PostgreSQL
 * updates the same schema row), which stopped a service from starting. Flyway's own lock is
 * per history table, so it doesn't keep services apart; this one does.
 *
 * <p>The lock is a session-level PostgreSQL advisory lock on its own connection: waiting is
 * cheap, and it is released when the migrations finish or the connection closes, even if the
 * service dies halfway.
 */
public class SharedSchemaMigrations implements FlywayMigrationStrategy {

    /** The same key in every service ("bayflow" in ASCII). */
    static final long LOCK_KEY = 0x62_61_79_66_6c_6f_77L;

    private static final Logger log = LoggerFactory.getLogger(SharedSchemaMigrations.class);

    @Override
    public void migrate(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                flyway.migrate();
                return;
            }
            try (Statement statement = connection.createStatement()) {
                log.debug("Waiting for the shared migration lock");
                statement.execute("SELECT pg_advisory_lock(" + LOCK_KEY + ")");
                try {
                    flyway.migrate();
                } finally {
                    statement.execute("SELECT pg_advisory_unlock(" + LOCK_KEY + ")");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not take the shared migration lock", e);
        }
    }
}
