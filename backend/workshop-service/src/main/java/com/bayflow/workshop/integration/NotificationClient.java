package com.bayflow.workshop.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.workshop.config.WorkshopProperties;

/** Delivers notification events to notification-service with a SYSTEM token. */
@Component
public class NotificationClient {

    private final RestClient notifications;
    private final SystemTokenProvider tokens;

    public NotificationClient(WorkshopProperties properties, SystemTokenProvider tokens) {
        this.notifications = HttpClients.create(properties.getNotifications().getUrl(), properties);
        this.tokens = tokens;
    }

    /** Throws when it was not stored (the outbox then retries); a repeat is acknowledged there. */
    public void send(NotificationPayload event) {
        try {
            post(event);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            post(event);
        }
    }

    private void post(NotificationPayload event) {
        notifications.post()
                .uri("/api/v1/internal/notification-events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
