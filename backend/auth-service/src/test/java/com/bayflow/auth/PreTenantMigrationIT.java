package com.bayflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import com.bayflow.security.Tenants;

/**
 * Multi-tenancy migration on existing data: a database at the last pre-tenant version (V2)
 * with users, roles and refresh tokens migrates, and every row lands in the default workshop.
 */
class PreTenantMigrationIT {

    @Test
    void existingAccountsMoveIntoTheDefaultWorkshop() throws Exception {
        String url = freshDatabase("auth_pre_tenant");
        flyway(url, "2").migrate();

        UUID userId = UUID.randomUUID();
        execute(url, "insert into auth_users (id, username, email, password_hash, created_at, updated_at) "
                + "values ('" + userId + "', 'old-user', 'old@example.com', '{noop}x', now(), now())");
        execute(url, "insert into auth_user_roles (user_id, role) values ('" + userId + "', 'CUSTOMER')");
        execute(url, "insert into auth_refresh_tokens (id, user_id, family_id, token_hash, expires_at, created_at) "
                + "values ('" + UUID.randomUUID() + "', '" + userId + "', '" + UUID.randomUUID()
                + "', 'hash', now() + interval '1 day', now())");

        flyway(url, null).migrate();

        String expected = Tenants.DEFAULT_TENANT_ID.toString();
        assertThat(single(url, "select tenant_id from auth_users where id = '" + userId + "'")).isEqualTo(expected);
        assertThat(single(url, "select tenant_id from auth_user_roles where user_id = '" + userId + "'"))
                .isEqualTo(expected);
        assertThat(single(url, "select tenant_id from auth_refresh_tokens where user_id = '" + userId + "'"))
                .isEqualTo(expected);
        // Phase 4 (V6): the default workshop is at demo.<base domain>.
        assertThat(single(url, "select slug from auth_tenants where id = '" + expected + "'")).isEqualTo("demo");
        // Locations (L1, V7): the organization's first location, "Main", has the tenant's id.
        assertThat(single(url, "select code from auth_locations where id = '" + expected + "' and tenant_id = '"
                + expected + "'")).isEqualTo("main");
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, IntegrationTestBase.POSTGRES.getUsername(), IntegrationTestBase.POSTGRES.getPassword())
                .schemas("bayflow")
                .defaultSchema("bayflow")
                .table("flyway_history_auth")
                .locations("classpath:db/migration")
                .placeholders(Map.of("default_tenant_id", Tenants.DEFAULT_TENANT_ID.toString(), "app_role", "bayflow_app",
                        "default_labour_rate", "95.00", "default_currency", "USD",
                        "default_time_zone", "America/New_York"));
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static String freshDatabase(String name) throws Exception {
        String jdbcUrl = IntegrationTestBase.POSTGRES.getJdbcUrl();
        execute(jdbcUrl, "DROP DATABASE IF EXISTS " + name);
        execute(jdbcUrl, "CREATE DATABASE " + name);
        int slash = jdbcUrl.lastIndexOf('/');
        int query = jdbcUrl.indexOf('?', slash);
        return jdbcUrl.substring(0, slash + 1) + name + (query < 0 ? "" : jdbcUrl.substring(query));
    }

    private static void execute(String url, String sql) throws Exception {
        try (Connection connection = connect(url); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String single(String url, String sql) throws Exception {
        try (Connection connection = connect(url); Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).as(sql).isTrue();
            return rs.getString(1);
        }
    }

    private static Connection connect(String url) throws Exception {
        Connection connection = DriverManager.getConnection(url, IntegrationTestBase.POSTGRES.getUsername(),
                IntegrationTestBase.POSTGRES.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to bayflow, public");
        }
        return connection;
    }
}
