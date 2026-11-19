package com.bayflow.appointment;

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
 * with a customer, vehicle, appointment and outbox message migrates, and every row lands in
 * the default workshop.
 */
class PreTenantMigrationIT {

    @Test
    void existingBookingsMoveIntoTheDefaultWorkshop() throws Exception {
        String url = freshDatabase("apt_pre_tenant");
        flyway(url, "2").migrate();

        UUID customer = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        UUID appointment = UUID.randomUUID();
        execute(url, "insert into apt_customers (id, user_id, name, email, created_at, updated_at) values ('"
                + customer + "', '" + UUID.randomUUID() + "', 'Old Customer', 'old@example.com', now(), now())");
        execute(url, "insert into apt_vehicles (id, customer_id, vin, registration, make, model, model_year, mileage,"
                + " created_at, updated_at) values ('" + vehicle + "', '" + customer
                + "', '1HGCM82633A004352', 'OLD 1', 'Honda', 'Accord', 2015, 90000, now(), now())");
        execute(url, "insert into apt_appointments (id, customer_id, vehicle_id, scheduled_at, ends_at,"
                + " duration_minutes, service_type, status, created_at, updated_at) values ('" + appointment
                + "', '" + customer + "', '" + vehicle + "', now() + interval '1 day', now() + interval '25 hours',"
                + " 60, 'ROUTINE_SERVICE', 'BOOKED', now(), now())");
        execute(url, "insert into apt_outbox (id, appointment_id, type, payload, next_attempt_at, created_at) values ('"
                + UUID.randomUUID() + "', '" + appointment + "', 'CREATE_JOB_CARD', '{}', now(), now())");

        flyway(url, null).migrate();

        String expected = Tenants.DEFAULT_TENANT_ID.toString();
        for (String table : new String[] { "apt_customers", "apt_vehicles", "apt_appointments", "apt_outbox" }) {
            assertThat(single(url, "select count(*) from " + table + " where tenant_id is distinct from '"
                    + expected + "'")).as(table).isEqualTo("0");
        }
        // Locations (L1, V5): the appointment is at the organization's "Main" (id = tenant id).
        assertThat(single(url, "select location_id from apt_appointments where id = '" + appointment + "'"))
                .isEqualTo(expected);
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, IntegrationTestBase.POSTGRES.getUsername(), IntegrationTestBase.POSTGRES.getPassword())
                .schemas("bayflow")
                .defaultSchema("bayflow")
                .table("flyway_history_appointment")
                .locations("classpath:db/migration")
                .placeholders(Map.of("default_tenant_id", Tenants.DEFAULT_TENANT_ID.toString(), "app_role", "bayflow_app"));
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
