package com.bayflow.auth.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.HourlyLimiter;
import com.bayflow.auth.application.SignupRateLimiter;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Onboarding step 3: the console's Workshops page, test workshops, suspend and reactivate. */
class ConsoleWorkshopsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private HourlyLimiter limiter;

    @Autowired
    private SignupRateLimiter signupLimiter;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        limiter.reset();
        signupLimiter.reset();
        String team = "team-" + suffix();
        newUser(Platform.TENANT_ID, team, team + "@example.com", PASSWORD, Set.of(Role.PLATFORM_ADMIN));
        token = JsonPath.read(body(login(team, Platform.CONSOLE_CLIENT, "localhost").andExpect(status().isOk())),
                "$.accessToken");
    }

    @Test
    void aTestWorkshopIsCreatedLabelledAndWorks() throws Exception {
        String slug = "test-" + suffix();
        console(post("/api/v1/auth/platform/workshops").contentType(MediaType.APPLICATION_JSON).content("""
                {"workshopName":"QA Garage %s","slug":"%s","currency":"USD","timeZone":"America/New_York",
                 "adminUsername":"qa-owner","adminEmail":"qa@%s.example","adminPassword":"%s"}"""
                .formatted(slug, slug, slug, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.test").value(true));

        login("qa-owner", "web", slug + ".localhost").andExpect(status().isOk());
        console(get("/api/v1/auth/platform/workshops").param("q", slug))
                .andExpect(jsonPath("$.content[0].slug").value(slug))
                .andExpect(jsonPath("$.content[0].test").value(true))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].staff").value(1))
                .andExpect(jsonPath("$.content[0].customers").value(0))
                .andExpect(jsonPath("$.content[0].locations").value(1))
                .andExpect(jsonPath("$.content[0].ownerUsername").value("qa-owner"))
                .andExpect(jsonPath("$.content[0].invitedEmail").doesNotExist());

        // The same address rules as sign-up.
        console(post("/api/v1/auth/platform/workshops").contentType(MediaType.APPLICATION_JSON).content("""
                {"workshopName":"Again","slug":"%s","adminUsername":"x-owner","adminEmail":"x@example.com",
                 "adminPassword":"%s"}""".formatted(slug, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void invitedWorkshopsShowTheirInviteAndTheConsoleItselfIsNotListed() throws Exception {
        String slug = "inv-" + suffix();
        mvc.perform(post("/api/v1/tenants/signup").header(CookieRequestGuard.HEADER, "web")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"workshopName":"Invited","slug":"%s","adminUsername":"owner",
                                 "adminEmail":"owner@%s.example","adminPassword":"%s","inviteToken":"%s"}"""
                                .formatted(slug, slug, PASSWORD, inviteToken())))
                .andExpect(status().isCreated());

        console(get("/api/v1/auth/platform/workshops").param("q", slug))
                .andExpect(jsonPath("$.content[0].test").value(false))
                .andExpect(jsonPath("$.content[0].invitedEmail").isString());
        console(get("/api/v1/auth/platform/workshops").param("test", "false").param("size", "100"))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem(Platform.SLUG))));
    }

    @Test
    void aSuspendedWorkshopCannotSignInUntilReactivated() throws Exception {
        String slug = "sus-" + suffix();
        String id = JsonPath.read(body(console(post("/api/v1/auth/platform/workshops")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"workshopName":"Pause Motors","slug":"%s","adminUsername":"owner",
                         "adminEmail":"owner@%s.example","adminPassword":"%s"}""".formatted(slug, slug, PASSWORD)))
                .andExpect(status().isCreated())), "$.id");

        console(post("/api/v1/auth/platform/workshops/" + id + "/suspend")).andExpect(status().isOk());
        login("owner", "web", slug + ".localhost")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:workshop-suspended"));
        console(get("/api/v1/auth/platform/workshops").param("status", "SUSPENDED").param("q", slug))
                .andExpect(jsonPath("$.content[0].slug").value(slug));

        console(post("/api/v1/auth/platform/workshops/" + id + "/reactivate")).andExpect(status().isOk());
        login("owner", "web", slug + ".localhost").andExpect(status().isOk());

        // Not the console's own organization, nor a workshop that doesn't exist.
        console(post("/api/v1/auth/platform/workshops/" + Platform.TENANT_ID + "/suspend"))
                .andExpect(status().isNotFound());
        console(post("/api/v1/auth/platform/workshops/" + UUID.randomUUID() + "/suspend"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheConsoleManagesWorkshops() throws Exception {
        String admin = "owner-" + suffix();
        newUser(Tenants.DEFAULT_TENANT_ID, admin, admin + "@example.com", PASSWORD, Set.of(Role.ADMIN));
        String workshopToken = JsonPath.read(body(login(admin, "web", "demo.localhost").andExpect(status().isOk())),
                "$.accessToken");
        mvc.perform(get("/api/v1/auth/platform/workshops").header("Authorization", "Bearer " + workshopToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/platform/workshops/" + Tenants.DEFAULT_TENANT_ID + "/suspend")
                        .header("Authorization", "Bearer " + workshopToken))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ---------------------------------------------------------------

    private ResultActions console(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions login(String name, String client, String host) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .header("X-Bayflow-Client", client)
                .header("X-Forwarded-Host", host)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(name, PASSWORD)));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
