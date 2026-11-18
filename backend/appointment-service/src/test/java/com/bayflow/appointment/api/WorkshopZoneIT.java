package com.bayflow.appointment.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.integration.WorkshopSettingsClient.WorkshopSettings;
import com.jayway.jsonpath.JsonPath;

/** SaaS phase 3: a workshop's "today" and date filters follow its own time zone setting. */
class WorkshopZoneIT extends IntegrationTestBase {

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 10:00 on Tue 17 Nov in New York, but already 04:00 on Wed 18 Nov in Auckland. */
    private static final Instant AT = Instant.parse("2026-11-17T15:00:00Z");

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();

    @Test
    void dateFiltersUseTheWorkshopsTimeZone() throws Exception {
        String vehicleId = vehicle();
        String id = book(vehicleId);

        inZone("Pacific/Auckland");
        count(vehicleId, "2026-11-18", 1, id);
        count(vehicleId, "2026-11-17", 0, null);

        inZone("America/New_York");
        count(vehicleId, "2026-11-17", 1, id);
        count(vehicleId, "2026-11-18", 0, null);
    }

    @Test
    void theConfiguredZoneIsUsedWhileSettingsAreUnavailable() throws Exception {
        String vehicleId = vehicle();
        String id = book(vehicleId);
        Mockito.doThrow(new ResourceAccessException("auth-service down"))
                .when(workshopSettings).get(ArgumentMatchers.any());

        // Fallback: bayflow.appointment.workshop-zone (America/New_York)
        count(vehicleId, "2026-11-17", 1, id);
    }

    private void inZone(String zone) {
        Mockito.doReturn(new WorkshopSettings(zone, "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any());
    }

    private void count(String vehicleId, String date, int expected, String id) throws Exception {
        var result = mvc.perform(get("/api/v1/appointments").with(advisor)
                        .param("vehicleId", vehicleId).param("date", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
        if (id != null) {
            result.andExpect(jsonPath("$.content[0].id").value(id));
        }
    }

    private String vehicle() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", customerId);
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kiri Tane\",\"email\":\"kiri@example.com\",\"phone\":\"+64 9 555 0100\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"NZ 1234","make":"Toyota","model":"Aqua","year":2020,"mileage":0}
                                """.formatted(randomVin())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }


    private String book(String vehicleId) throws Exception {
        String body = mvc.perform(post("/api/v1/appointments").with(as("CUSTOMER", customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":"%s","scheduledAt":"%s","serviceType":"ROUTINE_SERVICE"}
                                """.formatted(vehicleId, AT)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String randomVin() {
        StringBuilder vin = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            vin.append(VIN_CHARS.charAt(RANDOM.nextInt(VIN_CHARS.length())));
        }
        return vin.toString();
    }
}
