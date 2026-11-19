package com.bayflow.appointment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Check-ins queued before multi-tenancy or before locations still open their job card. */
class OpenJobCardPayloadTest {

    private final UUID rowTenant = UUID.randomUUID();

    private static OpenJobCardPayload payload(UUID tenant, UUID location) {
        return new OpenJobCardPayload(UUID.randomUUID(), UUID.randomUUID(), "VIN", "REG", UUID.randomUUID(),
                "Jane", 10, tenant, location);
    }

    @Test
    void oldMessagesGetTheRowsTenantAndItsFirstLocation() {
        OpenJobCardPayload filled = payload(null, null).withDefaults(rowTenant);
        assertThat(filled.tenantId()).isEqualTo(rowTenant);
        assertThat(filled.locationId()).isEqualTo(rowTenant);

        UUID tenant = UUID.randomUUID();
        OpenJobCardPayload beforeLocations = payload(tenant, null).withDefaults(rowTenant);
        assertThat(beforeLocations.tenantId()).isEqualTo(tenant);
        assertThat(beforeLocations.locationId()).isEqualTo(tenant);
    }

    @Test
    void currentMessagesAreLeftAsTheyAre() {
        UUID tenant = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        OpenJobCardPayload current = payload(tenant, location).withDefaults(rowTenant);
        assertThat(current.tenantId()).isEqualTo(tenant);
        assertThat(current.locationId()).isEqualTo(location);
    }
}
