package com.bayflow.workshop;

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
 * with a job card and a finding migrates, and every row lands in the default workshop.
 */
class PreTenantMigrationIT {

    @Test
    void existingJobCardsMoveIntoTheDefaultWorkshop() throws Exception {
        String url = freshDatabase("ws_pre_tenant");
        flyway(url, "2").migrate();

        UUID card = UUID.randomUUID();
        execute(url, "insert into ws_job_cards (id, appointment_id, vehicle_id, vin, registration, customer_name,"
                + " mileage, status, created_at, updated_at) values ('" + card + "', '" + UUID.randomUUID() + "', '"
                + UUID.randomUUID() + "', '1HGCM82633A004352', 'OLD 1', 'Old Customer', 1000, 'OPEN', now(), now())");
        execute(url, "insert into ws_inspection_findings (id, job_card_id, description, severity, recorded_by,"
                + " created_at) values ('" + UUID.randomUUID() + "', '" + card + "', 'Worn pads', 'HIGH', 'mech', now())");

        flyway(url, null).migrate();

        String expected = Tenants.DEFAULT_TENANT_ID.toString();
        for (String table : new String[] { "ws_job_cards", "ws_inspection_findings" }) {
            assertThat(single(url, "select count(*) from " + table + " where tenant_id is distinct from '"
                    + expected + "'")).as(table).isEqualTo("0");
            assertThat(single(url, "select count(*) from " + table)).as(table).isEqualTo("1");
        }
        // Locations (L1, V6): the card is at the organization's "Main" (id = tenant id).
        assertThat(single(url, "select location_id from ws_job_cards where id = '" + card + "'")).isEqualTo(expected);
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword())
                .schemas("bayflow")
                .defaultSchema("bayflow")
                .table("flyway_history_workshop")
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
        String jdbcUrl = PostgresContainer.INSTANCE.getJdbcUrl();
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
        Connection connection = DriverManager.getConnection(url, PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to bayflow, public");
        }
        return connection;
    }
}
