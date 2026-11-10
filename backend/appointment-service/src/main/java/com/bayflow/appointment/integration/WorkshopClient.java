package com.bayflow.appointment.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;

/** Calls workshop-service with a SYSTEM token. */
@Component
public class WorkshopClient {

    private final RestClient workshop;
    private final SystemTokenProvider tokens;

    public WorkshopClient(IntegrationProperties properties, SystemTokenProvider tokens) {
        this.workshop = HttpClients.create(properties.getWorkshopUrl(), properties);
        this.tokens = tokens;
    }

    /**
     * Opens (or returns the existing) job card for the appointment. A 401 usually means the
     * cached token is stale, so the token is refreshed once and the call repeated.
     */
    public JobCardRef openJobCard(OpenJobCardPayload payload) {
        try {
            return post(payload);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            return post(payload);
        }
    }

    private JobCardRef post(OpenJobCardPayload payload) {
        JobCardRef ref = workshop.post()
                .uri("/api/v1/job-cards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JobCardRef.class);
        if (ref == null || ref.id() == null) {
            throw new IllegalStateException("workshop-service returned no job card id");
        }
        return ref;
    }
}
