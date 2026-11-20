package com.bayflow.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Several announcements per workshop: the ADMIN keeps up to 10, everyone at the workshop's
 * address gets the live ones, important first, then the one ending soonest.
 */
class AnnouncementsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final String PATH = "/api/v1/auth/tenant/branding/announcements";
    private static final UUID OTHER = UUID.fromString("0c0c0c0c-0000-4000-8000-0000000000b2");
    private static final String OTHER_HOST = "announcements-other.localhost:3000";

    private final JdbcTemplate jdbc = ownerJdbc();
    private final Instant now = Instant.now();

    @BeforeEach
    void clean() {
        jdbc.update("""
                insert into bayflow.auth_tenants (id, slug, name, status, created_at, updated_at)
                values (?, 'announcements-other', 'Kiwi Motors', 'ACTIVE', now(), now())
                on conflict (id) do nothing""", OTHER);
        jdbc.update("delete from bayflow.auth_tenant_announcements");
    }

    @Test
    void liveOnesAreShownImportantFirstThenEndingSoonest() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        create(admin, "Saturdays open", "INFO", null, null).andExpect(status().isCreated());
        create(admin, "Winter check-up", "INFO", null, now.plus(20, ChronoUnit.DAYS));
        create(admin, "Holiday hours", "IMPORTANT", null, now.plus(40, ChronoUnit.DAYS));
        create(admin, "Black Friday", "INFO", now.plus(5, ChronoUnit.DAYS), now.plus(8, ChronoUnit.DAYS));
        create(admin, "Summer A/C", "INFO", now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));

        mvc.perform(get("/api/v1/tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.announcements.length()").value(3))
                .andExpect(jsonPath("$.branding.announcements[0].text").value("Holiday hours"))
                .andExpect(jsonPath("$.branding.announcements[0].style").value("IMPORTANT"))
                .andExpect(jsonPath("$.branding.announcements[1].text").value("Winter check-up"))
                .andExpect(jsonPath("$.branding.announcements[2].text").value("Saturdays open"));

        // The admin sees all of them, oldest first, with where each is in its dates.
        mvc.perform(get(PATH).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].text").value("Saturdays open"))
                .andExpect(jsonPath("$[0].state").value("LIVE"))
                .andExpect(jsonPath("$[3].state").value("SCHEDULED"))
                .andExpect(jsonPath("$[4].state").value("ENDED"));
    }

    @Test
    void anAdminChangesAndDeletesThem() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String body = create(admin, "Closed Monday", "INFO", null, null).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.id");

        update(admin, id, "Closed Monday 24 Dec", "IMPORTANT", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Closed Monday 24 Dec"))
                .andExpect(jsonPath("$.style").value("IMPORTANT"))
                .andExpect(jsonPath("$.version").value(1));
        // A stale version is refused.
        update(admin, id, "Someone else's edit", "INFO", 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:concurrent-update"));

        mvc.perform(delete(PATH + "/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tenants/current")).andExpect(jsonPath("$.branding.announcements").isEmpty());
        mvc.perform(delete(PATH + "/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void textAndDatesAreChecked() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        create(admin, "   ", "INFO", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-announcement"));
        create(admin, "x".repeat(201), "INFO", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-announcement"));
        create(admin, "Holiday", "INFO", now.plus(2, ChronoUnit.DAYS), now).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-announcement-window"));
        mvc.perform(post(PATH).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hi\",\"style\":\"SHOUTING\"}")).andExpect(status().isBadRequest());

        // One line of plain text: HTML stays text, line breaks become spaces.
        create(admin, "Line one\\nline\\ttwo   <b>bold</b>", "INFO", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Line one line two <b>bold</b>"));
    }

    @Test
    void aWorkshopKeepsAtMostTen() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        for (int i = 1; i <= 10; i++) {
            create(admin, "Offer " + i, "INFO", null, null).andExpect(status().isCreated());
        }
        create(admin, "Offer 11", "INFO", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:too-many-announcements"));
    }

    @Test
    void onlyAnAdminKeepsThemAndWorkshopsNeverSeeEachOthers() throws Exception {
        for (Role role : new Role[] { Role.CUSTOMER, Role.SERVICE_ADVISOR, Role.MECHANIC, Role.MANAGER }) {
            String token = tokenFor(role);
            mvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            create(token, "Hijacked", "INFO", null, null).andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hi\"}"))
                .andExpect(status().isUnauthorized());

        create(tokenFor(Role.ADMIN), "Demo only", "INFO", null, null).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/tenants/current").header("X-Forwarded-Host", OTHER_HOST))
                .andExpect(jsonPath("$.slug").value("announcements-other"))
                .andExpect(jsonPath("$.branding.announcements").isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------------

    private ResultActions create(String token, String text, String style, Instant starts, Instant ends)
            throws Exception {
        return mvc.perform(post(PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"%s\",\"style\":\"%s\",\"startsAt\":%s,\"endsAt\":%s}"
                        .formatted(text, style, json(starts), json(ends))));
    }

    private ResultActions update(String token, String id, String text, String style, long version)
            throws Exception {
        return mvc.perform(put(PATH + "/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"%s\",\"style\":\"%s\",\"version\":%d}".formatted(text, style, version)));
    }

    private String tokenFor(Role role) throws Exception {
        String username = "ann-" + role.name().toLowerCase().replace('_', '-') + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        newUser(Tenants.DEFAULT_TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String json(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
