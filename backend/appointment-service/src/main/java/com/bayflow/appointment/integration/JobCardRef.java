package com.bayflow.appointment.integration;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The part of workshop-service's job card response that appointment-service needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobCardRef(UUID id, String status) {
}
