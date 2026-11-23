package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.mail.MailDispatcher;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** E-mail queued for other services (invoices from workshop-service): SYSTEM only, sent through the outbox. */
@Import(PasswordResetByEmailIT.CapturedMail.class)
class ServiceMailIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private PasswordResetByEmailIT.CapturingGateway mail;

    @Autowired
    private MailDispatcher dispatcher;

    @AfterEach
    void restore() {
        mail.available = true;
    }

    @Test
    void aServiceQueuesAFinishedEmail() throws Exception {
        String to = "invoice-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        send(systemToken(), Tenants.DEFAULT_TENANT_ID, body(to)).andExpect(status().isAccepted());
        dispatcher.dispatchDue();

        assertThat(mail.to(to)).singleElement().satisfies(sent -> {
            assertThat(sent.subject()).isEqualTo("Invoice INV-1001 from Main Street Garage");
            assertThat(sent.senderName()).isEqualTo("Main Street Garage");
            assertThat(sent.replyTo()).isEqualTo("desk@example.com");
            assertThat(sent.text()).contains("Total");
            assertThat(sent.html()).contains("<table>");
        });
    }

    @Test
    void onlyServicesMaySendAndOnlyWhenEmailIsSetUp() throws Exception {
        String to = "nobody-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        send(staffToken(), Tenants.DEFAULT_TENANT_ID, body(to)).andExpect(status().isForbidden());
        mvc.perform(post(path(Tenants.DEFAULT_TENANT_ID)).contentType(MediaType.APPLICATION_JSON).content(body(to)))
                .andExpect(status().isUnauthorized());

        String system = systemToken();
        send(system, UUID.randomUUID(), body(to)).andExpect(status().isNotFound());
        send(system, Tenants.DEFAULT_TENANT_ID, body("not-an-address")).andExpect(status().isBadRequest());

        mail.available = false;
        send(system, Tenants.DEFAULT_TENANT_ID, body(to)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:mail-unavailable"));
        dispatcher.dispatchDue();
        assertThat(mail.to(to)).isEmpty();
    }

    private ResultActions send(String token, UUID tenant, String body) throws Exception {
        return mvc.perform(post(path(tenant)).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String path(UUID tenant) {
        return "/api/v1/auth/internal/tenants/" + tenant + "/mail";
    }

    private static String body(String to) {
        return """
                {"to":"%s","senderName":"Main Street Garage","replyTo":"desk@example.com",
                 "subject":"Invoice INV-1001 from Main Street Garage","text":"Total 10.00",
                 "html":"<html><body><table><tr><td>Total</td></tr></table></body></html>"}""".formatted(to);
    }

    private String staffToken() throws Exception {
        String username = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        newUser(Tenants.DEFAULT_TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(Role.ADMIN));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String systemToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"appointment-service\",\"clientSecret\":\"test-client-secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
