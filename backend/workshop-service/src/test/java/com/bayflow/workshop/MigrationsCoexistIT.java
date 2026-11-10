package com.bayflow.workshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

/**
 * All three services keep their tables in one schema, each with its own Flyway history
 * table. This runs every service's real migrations against one fresh database, in an order
 * where the schema is already non-empty for two of them, exactly as services start in any
 * order in production.
 */
class MigrationsCoexistIT {

    private static final String[][] SERVICES = {
            // service directory, history table
            { "appointment-service", "flyway_history_appointment" },
            { "auth-service", "flyway_history_auth" },
            { "workshop-service", "flyway_history_workshop" },
    };

    @Test
    void allServicesMigrateTheSharedSchemaWithoutConflict() throws Exception {
        String url = freshDatabase("coexist");

        for (String[] service : SERVICES) {
            MigrateResult result = flyway(url, service[0], service[1]).migrate();
            assertThat(result.success).as(service[0]).isTrue();
            assertThat(result.migrationsExecuted).as(service[0]).isPositive();
        }

        List<String> tables = tables(url);
        assertThat(tables).contains(
                "auth_users", "auth_user_roles", "auth_refresh_tokens", "auth_clients",
                "apt_customers", "apt_vehicles", "apt_appointments",
                "ws_job_cards", "ws_inspection_findings", "ws_recommended_items",
                "flyway_history_auth", "flyway_history_appointment", "flyway_history_workshop");

        // Starting again (e.g. a restart) applies nothing new.
        for (String[] service : SERVICES) {
            assertThat(flyway(url, service[0], service[1]).migrate().migrationsExecuted).as(service[0]).isZero();
        }
    }

    private static Flyway flyway(String url, String serviceDirectory, String historyTable) {
        Path migrations = Path.of("..", serviceDirectory, "src", "main", "resources", "db", "migration")
                .toAbsolutePath().normalize();
        assertThat(Files.isDirectory(migrations)).as(migrations.toString()).isTrue();
        // Same settings as each service's application.yml
        return Flyway.configure()
                .dataSource(url, PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword())
                .schemas("bayflow")
                .defaultSchema("bayflow")
                .table(historyTable)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("filesystem:" + migrations)
                .load();
    }

    private static String freshDatabase(String name) throws Exception {
        try (Connection connection = DriverManager.getConnection(PostgresContainer.INSTANCE.getJdbcUrl(),
                PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        }
        String jdbcUrl = PostgresContainer.INSTANCE.getJdbcUrl();
        int slash = jdbcUrl.lastIndexOf('/');
        int query = jdbcUrl.indexOf('?', slash);
        return jdbcUrl.substring(0, slash + 1) + name + (query < 0 ? "" : jdbcUrl.substring(query));
    }

    private static List<String> tables(String url) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select table_name from information_schema.tables where table_schema = 'bayflow'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
