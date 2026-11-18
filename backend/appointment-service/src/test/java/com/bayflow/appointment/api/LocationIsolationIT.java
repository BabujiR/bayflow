package com.bayflow.appointment.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Organizations and locations, phase L2: staff work only with the appointments of their
 * locations. Everything else is 404, as for another organization's data; the location switcher
 * header narrows a view but can't widen it (403).
 */
class LocationIsolationIT extends IntegrationTestBase {

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;
    private static final UUID NORTH = UUID.fromString("0d0d0d0d-1111-4000-8000-0000000000d1");
    private static final Instant AT = Instant.parse("2026-11-17T15:00:00Z");

    private final UUID customerId = UUID.randomUUID();
    private final RequestPostProcessor mainAdvisor = atLocations("SERVICE_ADVISOR", UUID.randomUUID(), MAIN);
    private final RequestPostProcessor northAdvisor = atLocations("SERVICE_ADVISOR", UUID.randomUUID(), NORTH);
    private final RequestPostProcessor regional = atLocations("SERVICE_ADVISOR", UUID.randomUUID(), MAIN, NORTH);

    private String vehicleId;
    private String atMain;
    private String atNorth;

    @BeforeEach
    void twoLocationsWithABookingEach() throws Exception {
        Mockito.doReturn(List.of(mainLocation(MAIN, "America/New_York"),
                new LocationSettings(NORTH, "north", "North", true, "America/New_York", new BigDecimal("95.00"), "USD")))
                .when(locationsClient).locations(ArgumentMatchers.any());
        vehicleId = vehicle();
        atMain = id(book(as("CUSTOMER", customerId), MAIN, AT));
        atNorth = id(book(as("CUSTOMER", customerId), NORTH, AT.plusSeconds(86_400)));
    }

    @Test
    void listsOnlyShowTheStaffMembersLocations() throws Exception {
        list(mainAdvisor, null).andExpect(jsonPath("$.content[*].id", hasItem(atMain)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(atNorth))));
        list(northAdvisor, null).andExpect(jsonPath("$.content[*].id", hasItem(atNorth)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(atMain))));
        list(regional, null).andExpect(jsonPath("$.totalElements").value(2));
        // The customer sees both of their own bookings.
        mvc.perform(get("/api/v1/appointments").with(as("CUSTOMER", customerId)).param("vehicleId", vehicleId))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void anotherLocationsAppointmentIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/appointments/" + atNorth).with(mainAdvisor)).andExpect(status().isNotFound());
        for (String action : new String[] { "confirm", "arrive", "cancel", "no-show" }) {
            mvc.perform(post("/api/v1/appointments/" + atNorth + "/" + action).with(mainAdvisor))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/appointments/" + atNorth + "/check-in").with(mainAdvisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":100}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/appointments/" + atNorth + "/reschedule").with(mainAdvisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + AT.plusSeconds(3 * 86_400) + "\"}"))
                .andExpect(status().isNotFound());
        // Its own location's appointment works.
        mvc.perform(post("/api/v1/appointments/" + atMain + "/confirm").with(mainAdvisor))
                .andExpect(status().isOk());
    }

    @Test
    void staffBookOnlyIntoTheirLocations() throws Exception {
        book(mainAdvisor, NORTH, AT.plusSeconds(5 * 86_400)).andExpect(status().isNotFound());
        // Without a location: the only one they work at.
        book(northAdvisor, null, AT.plusSeconds(6 * 86_400)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(NORTH.toString()));
        // Working at several: they must say which.
        book(regional, null, AT.plusSeconds(7 * 86_400)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:location-required"));
    }

    @Test
    void theSwitcherNarrowsButNeverWidens() throws Exception {
        list(regional, NORTH.toString()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(atNorth));
        list(regional, "all").andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/appointments").with(mainAdvisor).param("vehicleId", vehicleId)
                        .header("X-Bayflow-Location", NORTH.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:location-not-allowed"));
        mvc.perform(get("/api/v1/appointments/" + atMain).with(regional)
                        .header("X-Bayflow-Location", NORTH.toString()))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ------------------------------------------------------------------------

    private ResultActions list(RequestPostProcessor who, String header) throws Exception {
        var request = get("/api/v1/appointments").with(who).param("vehicleId", vehicleId);
        if (header != null) {
            request.header("X-Bayflow-Location", header);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private String vehicle() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", customerId);
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Silva\",\"email\":\"ana@example.com\",\"phone\":\"+1 555 0100\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"LOC 1","make":"Kia","model":"Rio","year":2020,"mileage":0}
                                """.formatted(randomVin())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions book(RequestPostProcessor who, UUID locationId, Instant at) throws Exception {
        String location = locationId == null ? "" : ",\"locationId\":\"" + locationId + "\"";
        return mvc.perform(post("/api/v1/appointments").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vehicleId":"%s","scheduledAt":"%s","serviceType":"ROUTINE_SERVICE"%s}
                        """.formatted(vehicleId, at, location)));
    }

    private static String randomVin() {
        StringBuilder vin = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            vin.append(VIN_CHARS.charAt(RANDOM.nextInt(VIN_CHARS.length())));
        }
        return vin.toString();
    }
}
