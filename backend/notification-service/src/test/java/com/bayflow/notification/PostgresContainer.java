package com.bayflow.notification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One PostgreSQL 16 container shared by every integration test in this module (singleton pattern). */
public final class PostgresContainer {

    public static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    /** The services' runtime login (not the table owner), as in docker-compose. */
    public static final String APP_USER = "bayflow_app";
    public static final String APP_PASSWORD = "bayflow_app";

    static {
        INSTANCE.start();
        try (Connection connection = DriverManager.getConnection(INSTANCE.getJdbcUrl(), INSTANCE.getUsername(),
                INSTANCE.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'bayflow_app') THEN
                            CREATE ROLE bayflow_app LOGIN PASSWORD 'bayflow_app' NOSUPERUSER NOBYPASSRLS;
                        END IF;
                    END $$""");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the app role", e);
        }
    }

    private PostgresContainer() {
    }
}
