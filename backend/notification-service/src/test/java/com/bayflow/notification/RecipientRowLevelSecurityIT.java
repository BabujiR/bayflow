package com.bayflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Below any Java code: the app login (bayflow_app) sees a workshop's notifications only for the
 * recipient named in app.recipient ('*' = this service's own work), and none without it.
 */
class RecipientRowLevelSecurityIT extends IntegrationTestBase {

    private static final String A = "0a0a0a0a-0000-4000-8000-00000000000a";
    private static final String B = "0b0b0b0b-0000-4000-8000-00000000000b";
    private final String alice = UUID.randomUUID().toString();
    private final String bob = UUID.randomUUID().toString();

    @BeforeEach
    void seed() {
        ownerJdbc().update("delete from bayflow.ntf_notifications");
        for (String[] row : new String[][] { { A, alice }, { A, bob }, { B, alice } }) {
            ownerJdbc().update("insert into bayflow.ntf_notifications (id, tenant_id, recipient_id, type, title, body,"
                    + " event_id, created_at) values (?::uuid, ?::uuid, ?::uuid, 'WORK_COMPLETED', 't', 'b', ?::uuid,"
                    + " now())", UUID.randomUUID().toString(), row[0], row[1], UUID.randomUUID().toString());
        }
    }

    @Test
    void eachRecipientSeesOnlyTheirOwnInTheirWorkshop() throws Exception {
        assertThat(visible(A, alice)).isEqualTo(1);
        assertThat(visible(A, bob)).isEqualTo(1);
        assertThat(visible(B, alice)).isEqualTo(1);
        assertThat(visible(A, "*")).isEqualTo(2);
        assertThat(visible(A, "")).isZero();
        assertThat(visible(A, null)).isZero();
        assertThat(visible("", "*")).isZero();
    }

    @Test
    void aUserCannotTouchSomeoneElsesNotificationsOrAnotherWorkshop() throws Exception {
        try (Connection app = app()) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement()) {
                set(statement, A, alice);
                assertThatThrownBy(() -> statement.execute(insert(A, bob)))
                        .isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
            }
            app.rollback();
            try (Statement statement = app.createStatement()) {
                set(statement, A, "*");
                assertThatThrownBy(() -> statement.execute(insert(B, alice)))
                        .isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
            }
            app.rollback();
            try (Statement statement = app.createStatement()) {
                set(statement, A, alice);
                // Reading (deleting) someone else's changes nothing: their rows are invisible.
                assertThat(statement.executeUpdate("delete from bayflow.ntf_notifications where"
                        + " recipient_id = '" + bob + "'")).isZero();
            }
            app.rollback();
        }
    }

    private long visible(String tenant, String recipient) throws SQLException {
        try (Connection app = app()) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement()) {
                set(statement, tenant, recipient);
                try (ResultSet rs = statement.executeQuery("select count(*) from bayflow.ntf_notifications")) {
                    rs.next();
                    return rs.getLong(1);
                }
            } finally {
                app.rollback();
            }
        }
    }

    private static void set(Statement statement, String tenant, String recipient) throws SQLException {
        statement.execute("select set_config('app.tenant', '" + tenant + "', true)");
        if (recipient != null) {
            statement.execute("select set_config('app.recipient', '" + recipient + "', true)");
        }
    }

    private static String insert(String tenant, String recipient) {
        return "insert into bayflow.ntf_notifications (id, tenant_id, recipient_id, type, title, body, event_id,"
                + " created_at) values ('" + UUID.randomUUID() + "', '" + tenant + "', '" + recipient
                + "', 'WORK_COMPLETED', 't', 'b', '" + UUID.randomUUID() + "', now())";
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(PostgresContainer.INSTANCE.getJdbcUrl(), PostgresContainer.APP_USER,
                PostgresContainer.APP_PASSWORD);
    }
}
