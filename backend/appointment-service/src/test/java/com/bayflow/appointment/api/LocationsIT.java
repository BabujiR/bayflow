package com.bayflow.appointment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Organizations and locations, phase L1: every appointment is at a location of its
 * organization, and a location's time zone defines its "today" (SaaS phase 3 had one zone per
 * workshop).
 */
class LocationsIT extends IntegrationTestBase {

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;
    private static final UUID NORTH = UUID.fromString("0b0b0b0b-1111-4000-8000-00000000000a");
    private static final UUID CLOSED = UUID.fromString("0b0b0b0b-2222-4000-8000-00000000000b");

    /** 10:00 on Tue 17 Nov in New York, but already 04:00 on Wed 18 Nov in Auckland. */
    private static final Instant AT = Instant.parse("2026-11-17T15:00:00Z");

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();

    // ---- booking -------------------------------------------------------------------------

    @Test
    void withOneLocationABookingNeedsNoLocation() throws Exception {
        String vehicleId = vehicle();

        book(vehicleId, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(MAIN.toString()));
        assertThat(ownerJdbc().queryForObject(
                "select count(*) from bayflow.apt_appointments where vehicle_id = ?::uuid and location_id = ?",
                Integer.class, vehicleId, MAIN)).isEqualTo(1);
    }

    @Test
    void withSeveralLocationsABookingNamesAnActiveOneOfTheOrganization() throws Exception {
        organizationHas(mainAt("America/New_York"), location(NORTH, "north", true, "America/Chicago"),
                location(CLOSED, "closed", false, "America/New_York"));
        String vehicleId = vehicle();

        book(vehicleId, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:location-required"));
        book(vehicleId, CLOSED).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-location"));
        book(vehicleId, UUID.randomUUID()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-location"));
        book(vehicleId, NORTH).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(NORTH.toString()));
    }

    @Test
    void whileLocationsAreUnavailableOnlyTheFirstLocationTakesBookings() throws Exception {
        String vehicleId = vehicle();
        Mockito.doThrow(new ResourceAccessException("auth-service down"))
                .when(locationsClient).locations(ArgumentMatchers.any());

        book(vehicleId, NORTH).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:locations-unavailable"));
        book(vehicleId, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(MAIN.toString()));
    }

    @Test
    void aNewLocationTakesBookingsAtOnce() throws Exception {
        // The cached answer predates North; asking for North fetches the locations again.
        Mockito.doReturn(List.of(mainAt("America/New_York")))
                .doReturn(List.of(mainAt("America/New_York"), location(NORTH, "north", true, "America/Chicago")))
                .when(locationsClient).locations(ArgumentMatchers.any());
        String vehicleId = vehicle();

        book(vehicleId, NORTH).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(NORTH.toString()));
        Mockito.verify(locationsClient).evict(Tenants.DEFAULT_TENANT_ID);
    }

    @Test
    void anArchivedLocationNoLongerMakesCustomersChooseOne() throws Exception {
        // The cached answer still has North; it was archived since, so the booking asks again
        // and goes to the only active location instead of answering location-required.
        Mockito.doReturn(List.of(mainAt("America/New_York"), location(NORTH, "north", true, "America/Chicago")))
                .doReturn(List.of(mainAt("America/New_York"), location(NORTH, "north", false, "America/Chicago")))
                .when(locationsClient).locations(ArgumentMatchers.any());
        String vehicleId = vehicle();

        book(vehicleId, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(MAIN.toString()));
        Mockito.verify(locationsClient).evict(Tenants.DEFAULT_TENANT_ID);
    }

    @Test
    void authServiceAsksForUpcomingAppointmentsBeforeArchiving() throws Exception {
        String vehicleId = vehicle();
        book(vehicleId, null).andExpect(status().isCreated());
        String url = "/api/v1/internal/tenants/" + MAIN + "/locations/" + MAIN + "/upcoming";

        mvc.perform(get(url).with(as("SYSTEM", UUID.randomUUID(), MAIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/internal/tenants/" + MAIN + "/locations/" + UUID.randomUUID() + "/upcoming")
                        .with(as("SYSTEM", UUID.randomUUID(), MAIN)))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get(url).with(advisor)).andExpect(status().isForbidden());
    }

    // ---- days are the location's days -----------------------------------------------------

    @Test
    void dateFiltersUseTheLocationsTimeZone() throws Exception {
        String vehicleId = vehicle();
        String id = JsonPath.read(book(vehicleId, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        organizationHas(mainAt("Pacific/Auckland"));
        count(vehicleId, "2026-11-18", null, 1, id);
        count(vehicleId, "2026-11-17", null, 0, null);

        organizationHas(mainAt("America/New_York"));
        count(vehicleId, "2026-11-17", null, 1, id);
        count(vehicleId, "2026-11-18", null, 0, null);
    }

    @Test
    void aListCanBeLimitedToOneLocationAndUsesItsDay() throws Exception {
        organizationHas(mainAt("America/New_York"), location(NORTH, "north", true, "Pacific/Auckland"));
        String vehicleId = vehicle();
        String atMain = JsonPath.read(book(vehicleId, MAIN).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String atNorth = JsonPath.read(book(vehicleId, NORTH, AT.plusSeconds(86_400)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        count(vehicleId, null, MAIN, 1, atMain);
        count(vehicleId, null, NORTH, 1, atNorth);
        // 18 Nov 15:00 UTC is 19 Nov in Auckland.
        count(vehicleId, "2026-11-19", NORTH, 1, atNorth);
        count(vehicleId, "2026-11-18", NORTH, 0, null);
    }

    @Test
    void theConfiguredZoneIsUsedWhileLocationsAreUnavailable() throws Exception {
        String vehicleId = vehicle();
        String id = JsonPath.read(book(vehicleId, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        Mockito.doThrow(new ResourceAccessException("auth-service down"))
                .when(locationsClient).locations(ArgumentMatchers.any());

        // Fallback: bayflow.appointment.workshop-zone (America/New_York)
        count(vehicleId, "2026-11-17", null, 1, id);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void organizationHas(LocationSettings... locations) {
        Mockito.doReturn(List.of(locations)).when(locationsClient).locations(ArgumentMatchers.any());
    }

    /** The organization's "Main" location, in {@code zone}. */
    private static LocationSettings mainAt(String zone) {
        return mainLocation(MAIN, zone);
    }

    private static LocationSettings location(UUID id, String code, boolean active, String zone) {
        return new LocationSettings(id, code, code, active, zone, new BigDecimal("95.00"), "USD");
    }

    private void count(String vehicleId, String date, UUID locationId, int expected, String id) throws Exception {
        var request = get("/api/v1/appointments").with(advisor).param("vehicleId", vehicleId);
        if (date != null) {
            request.param("date", date);
        }
        if (locationId != null) {
            request.param("locationId", locationId.toString());
        }
        var result = mvc.perform(request).andExpect(status().isOk())
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

    private ResultActions book(String vehicleId, UUID locationId) throws Exception {
        return book(vehicleId, locationId, AT);
    }

    private ResultActions book(String vehicleId, UUID locationId, Instant at) throws Exception {
        String location = locationId == null ? "" : ",\"locationId\":\"" + locationId + "\"";
        return mvc.perform(post("/api/v1/appointments").with(as("CUSTOMER", customerId))
                .contentType(MediaType.APPLICATION_JSON)
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
