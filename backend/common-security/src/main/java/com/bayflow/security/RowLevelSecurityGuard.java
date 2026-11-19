package com.bayflow.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;

/**
 * Refuses to start a service whose database login would ignore row-level security.
 * <p>
 * PostgreSQL does not apply RLS policies to superusers, to roles with BYPASSRLS, or to a
 * table's owner (RLS is enabled, not forced). If a service were configured with such a login,
 * workshop isolation would silently stop working at the database, so it fails at startup.
 */
public class RowLevelSecurityGuard implements InitializingBean {

    private final DataSource dataSource;
    private final String schema;

    public RowLevelSecurityGuard(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        check();
    }

    public void check() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String user;
            boolean privileged;
            try (PreparedStatement statement = connection.prepareStatement(
                    "select current_user, rolsuper or rolbypassrls from pg_roles where rolname = current_user");
                    ResultSet rs = statement.executeQuery()) {
                rs.next();
                user = rs.getString(1);
                privileged = rs.getBoolean(2);
            }
            if (privileged) {
                throw new IllegalStateException("Database user '" + user + "' is a superuser or has BYPASSRLS, so "
                        + "row-level security would not apply. Connect the service as the app role (bayflow_app).");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "select count(*) from pg_tables where schemaname = ? and tableowner = current_user")) {
                statement.setString(1, schema);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) > 0) {
                        throw new IllegalStateException("Database user '" + user + "' owns tables in schema '"
                                + schema + "', so row-level security would not apply to it. Use the owner only for "
                                + "Flyway (spring.flyway.user) and the app role for the service.");
                    }
                }
            }
        }
    }
}
