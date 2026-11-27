package com.bayflow.workshop.integration;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.ops.RequestIds;
import com.bayflow.workshop.config.WorkshopProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Asks appointment-service, with a SYSTEM token, who to bill for an appointment's job: the
 * customer's name, email and phone and the vehicle's make and model, copied onto the invoice.
 */
@Component
public class BillingContactClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BillingContact(UUID customerId, String name, String email, String phone, String vehicleMake,
            String vehicleModel, Integer vehicleYear) {
    }

    private final RestClient appointments;
    private final SystemTokenProvider tokens;

    public BillingContactClient(WorkshopProperties properties, SystemTokenProvider tokens) {
        this.appointments = RestClient.builder()
                .requestInterceptor(RequestIds.propagate())
                .baseUrl(properties.getAppointmentUrl())
                .requestFactory(SystemTokenProvider.factory(properties)).build();
        this.tokens = tokens;
    }

    /** Throws when appointment-service can't be reached or doesn't know the appointment. */
    public BillingContact get(UUID tenantId, UUID appointmentId) {
        try {
            return fetch(tenantId, appointmentId);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            return fetch(tenantId, appointmentId);
        }
    }

    private BillingContact fetch(UUID tenantId, UUID appointmentId) {
        return appointments.get()
                .uri("/api/v1/internal/tenants/{tenant}/appointments/{id}/billing", tenantId, appointmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .retrieve()
                .body(BillingContact.class);
    }
}
