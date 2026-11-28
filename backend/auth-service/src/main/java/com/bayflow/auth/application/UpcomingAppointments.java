package com.bayflow.auth.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.http.ServiceHttp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Asks appointment-service how many upcoming (booked, confirmed or arrived) appointments a
 * location has, before it is archived. auth-service signs its own SYSTEM token for the call.
 */
@Component
public class UpcomingAppointments {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Upcoming(long count) {
    }

    private final RestClient appointments;
    private final TokenService tokens;

    public UpcomingAppointments(AuthProperties properties, TokenService tokens) {
        this.appointments = ServiceHttp.internal(properties.getAppointmentUrl(), Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        this.tokens = tokens;
    }

    /** Upcoming appointments at the location; throws when appointment-service can't be reached. */
    public long count(UUID tenantId, UUID locationId) {
        String token = tokens.forClient("auth-service", List.of("SYSTEM")).value();
        Upcoming upcoming = appointments.get()
                .uri("/api/v1/internal/tenants/{tid}/locations/{lid}/upcoming", tenantId, locationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Upcoming.class);
        if (upcoming == null) {
            throw new IllegalStateException("appointment-service returned no answer");
        }
        return upcoming.count();
    }
}
