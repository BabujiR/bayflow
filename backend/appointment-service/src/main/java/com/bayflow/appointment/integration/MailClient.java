package com.bayflow.appointment.integration;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;

/**
 * Sends an e-mail through auth-service, which owns the mail server and its outbox (sent and
 * retried there). Callers treat a failure as "not sent": a booking stands without its e-mail.
 */
@Component
public class MailClient {

    /** A finished e-mail. */
    public record Mail(String to, String senderName, String replyTo, String subject, String text, String html) {
    }

    private final RestClient auth;
    private final SystemTokenProvider tokens;

    public MailClient(IntegrationProperties properties, SystemTokenProvider tokens) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
        this.tokens = tokens;
    }

    /** Queues the e-mail in auth-service; throws when it can't (e-mail off: 503, or unreachable). */
    public void send(UUID tenantId, Mail mail) {
        try {
            post(tenantId, mail);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            post(tenantId, mail);
        }
    }

    private void post(UUID tenantId, Mail mail) {
        auth.post()
                .uri("/api/v1/auth/internal/tenants/{tenant}/mail", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mail)
                .retrieve()
                .toBodilessEntity();
    }
}
