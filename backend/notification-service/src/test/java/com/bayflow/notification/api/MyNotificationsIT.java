package com.bayflow.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bayflow.notification.IntegrationTestBase;
import com.bayflow.notification.application.NotificationService;
import com.bayflow.security.Tenants;

/** Each user reads and marks only their own notifications, in their own workshop. */
class MyNotificationsIT extends IntegrationTestBase {

    private static final UUID OTHER_TENANT = UUID.fromString("0c0c0c0c-0000-4000-8000-0000000000c3");

    @Autowired
    private NotificationService service;

    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @BeforeEach
    void clean() {
        ownerJdbc().update("delete from bayflow.ntf_notifications");
    }

    @Test
    void aUserSeesOnlyTheirOwnNewestFirst() throws Exception {
        notify(me, Tenants.DEFAULT_TENANT_ID, "Older", Instant.now().minus(2, ChronoUnit.HOURS));
        notify(me, Tenants.DEFAULT_TENANT_ID, "Newer", Instant.now().minus(1, ChronoUnit.HOURS));
        notify(someoneElse, Tenants.DEFAULT_TENANT_ID, "Not yours", Instant.now());

        mvc.perform(get("/api/v1/notifications").with(user("CUSTOMER", me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Newer"))
                .andExpect(jsonPath("$.content[1].title").value("Older"));
        mvc.perform(get("/api/v1/notifications/unread-count").with(user("CUSTOMER", me)))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void readingANotificationDeletesIt() throws Exception {
        UUID first = notify(me, Tenants.DEFAULT_TENANT_ID, "First", Instant.now().minus(1, ChronoUnit.HOURS));
        notify(me, Tenants.DEFAULT_TENANT_ID, "Second", Instant.now());

        mvc.perform(post("/api/v1/notifications/" + first + "/read").with(user("CUSTOMER", me)))
                .andExpect(status().isNoContent());

        assertThat(exists(first)).isFalse();
        mvc.perform(get("/api/v1/notifications").with(user("CUSTOMER", me)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Second"));
        mvc.perform(get("/api/v1/notifications/unread-count").with(user("CUSTOMER", me)))
                .andExpect(jsonPath("$.count").value(1));
        // Reading it again: it is gone.
        mvc.perform(post("/api/v1/notifications/" + first + "/read").with(user("CUSTOMER", me)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyYourOwnCanBeRead() throws Exception {
        UUID theirs = notify(someoneElse, Tenants.DEFAULT_TENANT_ID, "Theirs", Instant.now());

        // Someone else's looks like it does not exist, and stays.
        mvc.perform(post("/api/v1/notifications/" + theirs + "/read").with(user("SERVICE_ADVISOR", me)))
                .andExpect(status().isNotFound());
        assertThat(exists(theirs)).isTrue();
    }

    @Test
    void readAllDeletesOnlyYourOwn() throws Exception {
        notify(me, Tenants.DEFAULT_TENANT_ID, "One", Instant.now());
        notify(me, Tenants.DEFAULT_TENANT_ID, "Two", Instant.now());
        UUID theirs = notify(someoneElse, Tenants.DEFAULT_TENANT_ID, "Theirs", Instant.now());

        mvc.perform(post("/api/v1/notifications/read-all").with(user("MECHANIC", me)))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/v1/notifications").with(user("MECHANIC", me)))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(exists(theirs)).isTrue();
    }

    @Test
    void anotherWorkshopsNotificationsStayThere() throws Exception {
        // Same user id in another workshop (ids are unique, but the database checks anyway).
        notify(me, OTHER_TENANT, "Elsewhere", Instant.now());

        mvc.perform(get("/api/v1/notifications").with(user("CUSTOMER", me)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/notifications").with(user("CUSTOMER", me, OTHER_TENANT)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void servicesAndStrangersHaveNoNotifications() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications").with(system())).andExpect(status().isForbidden());
    }

    @Test
    void notificationsOlderThan90DaysAreDeleted() throws Exception {
        UUID old = notify(me, Tenants.DEFAULT_TENANT_ID, "Old", Instant.now().minus(91, ChronoUnit.DAYS));
        UUID recent = notify(me, OTHER_TENANT, "Recent", Instant.now().minus(89, ChronoUnit.DAYS));

        assertThat(service.deleteOld()).isEqualTo(1);

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private UUID notify(UUID recipient, UUID tenant, String title, Instant at) {
        UUID id = UUID.randomUUID();
        ownerJdbc().update("""
                insert into bayflow.ntf_notifications (id, tenant_id, location_id, recipient_id, type, title, body,
                    link, event_id, created_at) values (?, ?, ?, ?, 'APPOINTMENT_CONFIRMED', ?, 'Body', '/appointments',
                    ?, ?)""", id, tenant, tenant, recipient, title, UUID.randomUUID(), Timestamp.from(at));
        return id;
    }

    private boolean exists(UUID id) {
        return ownerJdbc().queryForObject("select count(*) from bayflow.ntf_notifications where id = ?", Long.class,
                id) == 1;
    }
}
