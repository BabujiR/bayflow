package com.bayflow.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.bayflow.security.RowLevelSecurityGuard;

/**
 * Row-level security checked at the database, below any Java code: all three services' real
 * migrations run on one fresh database, then the app login (bayflow_app) tries to read and
 * write across workshops directly with SQL.
 */
class RowLevelSecurityIT {

    private static final String[][] SERVICES = {
            { "auth-service", "flyway_history_auth" },
            { "appointment-service", "flyway_history_appointment" },
            { "workshop-service", "flyway_history_workshop" },
    };
    private static final String A = "0a0a0a0a-0000-4000-8000-00000000000a";
    private static final String B = "0b0b0b0b-0000-4000-8000-00000000000b";

    private static String url;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        url = freshDatabase("rls_check");
        for (String[] service : SERVICES) {
            Path migrations = Path.of("..", service[0], "src", "main", "resources", "db", "migration")
                    .toAbsolutePath().normalize();
            Flyway.configure()
                    .dataSource(url, PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword())
                    .schemas("bayflow").defaultSchema("bayflow").table(service[1])
                    .baselineOnMigrate(true).baselineVersion("0")
                    .locations("filesystem:" + migrations)
                    .placeholders(Map.of("default_tenant_id", "00000000-0000-0000-0000-000000000001",
                            "app_role", PostgresContainer.APP_USER))
                    .load().migrate();
        }
        try (Connection owner = owner(); Statement statement = owner.createStatement()) {
            for (String tenant : new String[] { A, B }) {
                statement.execute("insert into bayflow.apt_customers (id, tenant_id, name, email, created_at, updated_at)"
                        + " values ('" + UUID.randomUUID() + "', '" + tenant + "', 'Customer of " + tenant
                        + "', 'c@example.com', now(), now())");
            }
        }
    }

    @Test
    void everyPerWorkshopTableHasRowLevelSecurityAndAPolicy() throws Exception {
        List<String> tables = perWorkshopTables();
        assertThat(tables).hasSize(14);
        try (Connection owner = owner(); Statement statement = owner.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs = statement.executeQuery("select c.relrowsecurity, (select count(*) from pg_policies p"
                        + " where p.schemaname = 'bayflow' and p.tablename = c.relname) from pg_class c"
                        + " join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'bayflow'"
                        + " and c.relname = '" + table + "'")) {
                    assertThat(rs.next()).as(table).isTrue();
                    assertThat(rs.getBoolean(1)).as(table + " has RLS enabled").isTrue();
                    assertThat(rs.getLong(2)).as(table + " has a policy").isPositive();
                }
            }
        }
    }

    @Test
    void withoutAWorkshopTheAppLoginSeesNoRowsAnywhere() throws Exception {
        try (Connection app = app(); Statement statement = app.createStatement()) {
            for (String table : perWorkshopTables()) {
                assertThat(count(statement, "select count(*) from bayflow." + table)).as(table).isZero();
            }
        }
        try (Connection owner = owner(); Statement statement = owner.createStatement()) {
            assertThat(count(statement, "select count(*) from bayflow.apt_customers")).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void aWorkshopSeesOnlyItsOwnRowsAndCannotWriteIntoAnother() throws Exception {
        try (Connection app = app()) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement()) {
                statement.execute("select set_config('app.tenant', '" + A + "', true)");
                assertThat(count(statement, "select count(*) from bayflow.apt_customers")).isEqualTo(1);
                assertThat(count(statement, "select count(*) from bayflow.apt_customers where tenant_id <> '"
                        + A + "'")).isZero();
                assertThatThrownBy(() -> statement.execute(
                        "insert into bayflow.apt_customers (id, tenant_id, name, email, created_at, updated_at)"
                                + " values ('" + UUID.randomUUID() + "', '" + B + "', 'Sneaky', 's@example.com',"
                                + " now(), now())"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
            app.rollback();
        }
    }

    @Test
    void theWorkshopSettingEndsWithItsTransaction() throws Exception {
        try (Connection app = app(); Statement statement = app.createStatement()) {
            app.setAutoCommit(false);
            statement.execute("select set_config('app.tenant', '" + A + "', true)");
            assertThat(count(statement, "select count(*) from bayflow.apt_customers")).isEqualTo(1);
            app.commit();

            // Same connection, next transaction: no workshop, no rows.
            assertThat(count(statement, "select count(*) from bayflow.apt_customers")).isZero();
            app.commit();
        }
    }

    @Test
    void theOutboxFunctionIsTheOnlyCrossWorkshopRead() throws Exception {
        try (Connection app = app(); Statement statement = app.createStatement()) {
            assertThat(count(statement, "select count(*) from bayflow.apt_due_outbox(now(), 10)")).isZero();
        }
    }

    @Test
    void theStartupGuardRejectsTheOwnerAndAcceptsTheAppLogin() throws Exception {
        var asOwner = new DriverManagerDataSource(url, PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword());
        assertThatThrownBy(() -> new RowLevelSecurityGuard(asOwner, "bayflow").check())
                .isInstanceOf(IllegalStateException.class);

        var asApp = new DriverManagerDataSource(url, PostgresContainer.APP_USER, PostgresContainer.APP_PASSWORD);
        new RowLevelSecurityGuard(asApp, "bayflow").check();
    }

    private static List<String> perWorkshopTables() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection owner = owner(); Statement statement = owner.createStatement();
                ResultSet rs = statement.executeQuery("select table_name from information_schema.columns"
                        + " where table_schema = 'bayflow' and column_name = 'tenant_id' order by table_name")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private static long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(url, PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword());
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(url, PostgresContainer.APP_USER, PostgresContainer.APP_PASSWORD);
    }

    private static String freshDatabase(String name) throws Exception {
        String jdbcUrl = PostgresContainer.INSTANCE.getJdbcUrl();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        }
        int slash = jdbcUrl.lastIndexOf('/');
        int query = jdbcUrl.indexOf('?', slash);
        return jdbcUrl.substring(0, slash + 1) + name + (query < 0 ? "" : jdbcUrl.substring(query));
    }
}
