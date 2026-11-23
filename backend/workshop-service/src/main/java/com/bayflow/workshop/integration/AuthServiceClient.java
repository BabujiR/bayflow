package com.bayflow.workshop.integration;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.workshop.config.WorkshopProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What invoice e-mails need from auth-service, with a SYSTEM token: the workshop's name and
 * contact details, the location's, and the sending itself (auth-service owns the mail server
 * settings and retries through its outbox).
 */
@Component
public class AuthServiceClient {

    /** The workshop: slug (its web address), organization and display name, contact details. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkshopProfile(String slug, String name, String displayName, String phone, String email,
            String address) {

        /** The name customers know: the display name, else the organization's name. */
        public String shownName() {
            return displayName != null && !displayName.isBlank() ? displayName : name;
        }
    }

    /** One location's name, time zone and contact details. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationProfile(String name, String timeZone, String phone, String email, String address) {
    }

    /** A finished e-mail. */
    public record Mail(String to, String senderName, String replyTo, String subject, String text, String html) {
    }

    /** auth-service has no mail server set up (it answered 503). */
    public static class MailUnavailableException extends RuntimeException {
        public MailUnavailableException() {
            super("E-mail is not set up in auth-service");
        }
    }

    private final RestClient auth;
    private final SystemTokenProvider tokens;

    public AuthServiceClient(WorkshopProperties properties, SystemTokenProvider tokens) {
        this.auth = RestClient.builder().baseUrl(properties.getAuthUrl())
                .requestFactory(SystemTokenProvider.factory(properties)).build();
        this.tokens = tokens;
    }

    public WorkshopProfile workshop(UUID tenantId) {
        return withToken(token -> auth.get()
                .uri("/api/v1/auth/internal/tenants/{tenant}/settings", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(WorkshopProfile.class));
    }

    public LocationProfile location(UUID tenantId, UUID locationId) {
        return withToken(token -> auth.get()
                .uri("/api/v1/auth/internal/tenants/{tenant}/locations/{location}/settings", tenantId, locationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(LocationProfile.class));
    }

    /** Queues the e-mail in auth-service; throws {@link MailUnavailableException} when e-mail is off. */
    public void send(UUID tenantId, Mail mail) {
        try {
            withToken(token -> auth.post()
                    .uri("/api/v1/auth/internal/tenants/{tenant}/mail", tenantId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mail)
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            throw new MailUnavailableException();
        }
    }

    private <T> T withToken(Function<String, T> call) {
        try {
            return call.apply(tokens.token());
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            return call.apply(tokens.token());
        }
    }
}
