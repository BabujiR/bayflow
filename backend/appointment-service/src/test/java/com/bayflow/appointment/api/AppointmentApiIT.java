package com.bayflow.appointment.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

class AppointmentApiIT extends IntegrationTestBase {

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Instant TOMORROW_10AM = Instant.parse("2026-11-17T15:00:00Z"); // 10:00 New York

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID());
    private final RequestPostProcessor admin = as("ADMIN", UUID.randomUUID());

    // ---- the main workflow ---------------------------------------------------------

    @Test
    void customerBooksAndAdvisorTakesItThroughCheckIn() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String vehicleId = vehicle(customer, 12_000);

        String id = book(customer, vehicleId, TOMORROW_10AM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.endsAt").value("2026-11-17T16:00:00Z"))
                .andExpect(jsonPath("$.vehicle.id").value(vehicleId))
                .andReturn().getResponse().getContentAsString();
        id = JsonPath.read(id, "$.id");

        action(advisor, id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        action(advisor, id, "arrive").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARRIVED"));
        checkIn(id, 12_480)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.arrivalMileage").value(12_480))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty())
                .andExpect(jsonPath("$.jobCardId").doesNotExist());

        mvc.perform(get("/api/v1/vehicles/" + vehicleId).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(12_480));
        mvc.perform(get("/api/v1/appointments/" + id).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    void transitionsOutOfOrderAreRejectedWith409() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String id = bookedId(customer, vehicle(customer, 1_000), TOMORROW_10AM);

        checkIn(id, 1_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-transition"));
        action(advisor, id, "arrive").andExpect(status().isConflict());
        action(advisor, id, "confirm").andExpect(status().isOk());
        action(advisor, id, "confirm").andExpect(status().isConflict());
        action(advisor, id, "cancel").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        action(advisor, id, "arrive").andExpect(status().isConflict());
        reschedule(advisor, id, TOMORROW_10AM.plus(Duration.ofDays(1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-transition"));
    }

    @Test
    void checkInMileageCannotBeLowerThanTheLastReading() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String id = bookedId(customer, vehicle(customer, 30_000), TOMORROW_10AM);
        action(advisor, id, "confirm");
        action(advisor, id, "arrive");

        checkIn(id, 29_999).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:mileage-decreased"));
    }

    // ---- double booking and rescheduling ---------------------------------------------

    @Test
    void aVehicleCannotHaveOverlappingActiveBookings() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String vehicleId = vehicle(customer, 0);
        String first = bookedId(customer, vehicleId, TOMORROW_10AM);

        book(customer, vehicleId, TOMORROW_10AM.plus(Duration.ofMinutes(30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:double-booking"));

        // Back-to-back is fine (slots are half-open), and so is another vehicle at the same time.
        book(customer, vehicleId, TOMORROW_10AM.plus(Duration.ofMinutes(60))).andExpect(status().isCreated());
        book(customer, vehicle(customer, 0), TOMORROW_10AM).andExpect(status().isCreated());

        // A cancelled booking frees its slot.
        action(customer, first, "cancel").andExpect(status().isOk());
        book(customer, vehicleId, TOMORROW_10AM.minus(Duration.ofMinutes(30))).andExpect(status().isCreated());
    }

    @Test
    void rescheduleKeepsTheStatusAndRespectsOtherBookings() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String vehicleId = vehicle(customer, 0);
        String first = bookedId(customer, vehicleId, TOMORROW_10AM);
        action(advisor, first, "confirm");
        bookedId(customer, vehicleId, TOMORROW_10AM.plus(Duration.ofHours(2)));

        reschedule(customer, first, TOMORROW_10AM.plus(Duration.ofMinutes(150)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:double-booking"));

        reschedule(customer, first, TOMORROW_10AM.plus(Duration.ofHours(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.scheduledAt").value("2026-11-17T19:00:00Z"));
    }

    @Test
    void bookingsMustBeInTheFuture() throws Exception {
        RequestPostProcessor customer = customerWithProfile();

        book(customer, vehicle(customer, 0), BASE.minus(Duration.ofMinutes(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:scheduled-in-past"));
    }

    @Test
    void noShowIsOnlyAllowedAfterTheScheduledTime() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String id = bookedId(customer, vehicle(customer, 0), BASE.plus(Duration.ofHours(2)));

        action(advisor, id, "no-show").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:too-early-for-no-show"));

        clock.advance(Duration.ofHours(3));
        action(advisor, id, "no-show").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    // ---- ownership and roles --------------------------------------------------------------

    @Test
    void customersOnlySeeAndChangeTheirOwnData() throws Exception {
        RequestPostProcessor alice = customerWithProfile();
        RequestPostProcessor bob = customerWithProfile();
        String aliceVehicle = vehicle(alice, 0);
        String aliceBooking = bookedId(alice, aliceVehicle, TOMORROW_10AM);

        mvc.perform(get("/api/v1/appointments/" + aliceBooking).with(bob)).andExpect(status().isNotFound());
        action(bob, aliceBooking, "cancel").andExpect(status().isNotFound());
        reschedule(bob, aliceBooking, TOMORROW_10AM.plus(Duration.ofDays(1))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehicles/" + aliceVehicle).with(bob)).andExpect(status().isNotFound());
        book(bob, aliceVehicle, TOMORROW_10AM.plus(Duration.ofDays(2))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/appointments?size=100").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(aliceBooking))));
        mvc.perform(get("/api/v1/appointments?size=100").with(alice))
                .andExpect(jsonPath("$.content[*].id", hasItem(aliceBooking)));
    }

    @Test
    void endpointsEnforceRoles() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String id = bookedId(customer, vehicle(customer, 0), TOMORROW_10AM);

        action(customer, id, "confirm").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:forbidden"));
        action(admin, id, "confirm").andExpect(status().isForbidden());
        action(mechanic, id, "cancel").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customers").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("X", "x@example.com")))
                .andExpect(status().isForbidden());

        // ADMIN has read-only access to everything.
        mvc.perform(get("/api/v1/appointments/" + id).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customers").with(admin)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/appointments")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:unauthorized"));
    }

    @Test
    void mechanicsOnlySeeTodaysCheckedInVehicles() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String todayCheckedIn = bookedId(customer, vehicle(customer, 0), BASE.plus(Duration.ofHours(1)));
        String todayBooked = bookedId(customer, vehicle(customer, 0), BASE.plus(Duration.ofHours(2)));
        String tomorrowCheckedIn = bookedId(customer, vehicle(customer, 0), TOMORROW_10AM);
        for (String id : new String[] { todayCheckedIn, tomorrowCheckedIn }) {
            action(advisor, id, "confirm");
            action(advisor, id, "arrive");
            checkIn(id, 10).andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/appointments?size=100").with(mechanic))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(todayCheckedIn)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(todayBooked))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(tomorrowCheckedIn))))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("BOOKED"))));
        mvc.perform(get("/api/v1/appointments/" + todayBooked).with(mechanic)).andExpect(status().isNotFound());
    }

    // ---- customers and vehicles ---------------------------------------------------------

    @Test
    void customersNeedAProfileBeforeAddingVehicles() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());

        mvc.perform(get("/api/v1/customers/me").with(customer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(null, randomVin(), 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:customer-profile-required"));
        mvc.perform(get("/api/v1/appointments").with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        createProfile(customer).andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("jane@example.com"));
        createProfile(customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:profile-exists"));

        mvc.perform(put("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("Jane Smith", "jane.smith@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Smith"));
    }

    @Test
    void advisorsManageCustomersAndVehiclesOnTheirBehalf() throws Exception {
        String name = "Walk-in " + UUID.randomUUID().toString().substring(0, 6);
        String customerId = JsonPath.read(mvc.perform(post("/api/v1/customers").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content(profileJson(name, "walkin@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/customers").param("q", name.toUpperCase()).with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(customerId));

        String vin = randomVin();
        String vehicleId = JsonPath.read(mvc.perform(post("/api/v1/vehicles").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson(customerId, vin, 500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/vehicles").with(advisor).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(customerId, vin.toLowerCase(), 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vin-taken"));
        mvc.perform(post("/api/v1/vehicles").with(advisor).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(null, randomVin(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:customer-required"));

        book(advisor, vehicleId, TOMORROW_10AM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.customerName").value(name));
    }

    @Test
    void advisorsFilterAppointmentsByDateStatusAndVehicle() throws Exception {
        RequestPostProcessor customer = customerWithProfile();
        String vehicleId = vehicle(customer, 0);
        String tomorrow = bookedId(customer, vehicleId, TOMORROW_10AM);
        String dayAfter = bookedId(customer, vehicleId, TOMORROW_10AM.plus(Duration.ofDays(1)));
        action(advisor, dayAfter, "confirm");

        mvc.perform(get("/api/v1/appointments").with(advisor)
                        .param("vehicleId", vehicleId).param("date", "2026-11-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(tomorrow));
        mvc.perform(get("/api/v1/appointments").with(advisor)
                        .param("vehicleId", vehicleId).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(dayAfter));
    }

    @Test
    void invalidRequestsGetFieldErrors() throws Exception {
        RequestPostProcessor customer = customerWithProfile();

        mvc.perform(post("/api/v1/appointments").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-11-20T15:00:00Z\",\"durationMinutes\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("vehicleId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("serviceType")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("durationMinutes")));
        mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(null, "NOT-A-VIN", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("vin")));
        mvc.perform(get("/api/v1/appointments/not-a-uuid").with(customer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/appointments/" + UUID.randomUUID()).with(advisor))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---- helpers ---------------------------------------------------------------------

    private RequestPostProcessor customerWithProfile() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());
        createProfile(customer).andExpect(status().isCreated());
        return customer;
    }

    private ResultActions createProfile(RequestPostProcessor customer) throws Exception {
        return mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content(profileJson("Jane Doe", "Jane@Example.com")));
    }

    private String vehicle(RequestPostProcessor customer, int mileage) throws Exception {
        String body = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(null, randomVin(), mileage)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions book(RequestPostProcessor who, String vehicleId, Instant at) throws Exception {
        return mvc.perform(post("/api/v1/appointments").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vehicleId":"%s","scheduledAt":"%s","serviceType":"ROUTINE_SERVICE","notes":"Noise on braking"}
                        """.formatted(vehicleId, at)));
    }

    private String bookedId(RequestPostProcessor who, String vehicleId, Instant at) throws Exception {
        String body = book(who, vehicleId, at).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions action(RequestPostProcessor who, String id, String action) throws Exception {
        return mvc.perform(post("/api/v1/appointments/" + id + "/" + action).with(who));
    }

    private ResultActions checkIn(String id, int mileage) throws Exception {
        return mvc.perform(post("/api/v1/appointments/" + id + "/check-in").with(advisor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":" + mileage + "}"));
    }

    private ResultActions reschedule(RequestPostProcessor who, String id, Instant at) throws Exception {
        return mvc.perform(patch("/api/v1/appointments/" + id + "/reschedule").with(who)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledAt\":\"" + at + "\"}"));
    }

    private static String profileJson(String name, String email) {
        return """
                {"name":"%s","email":"%s","phone":"+1 555 0100"}
                """.formatted(name, email);
    }

    private static String vehicleJson(String customerId, String vin, int mileage) {
        String customer = customerId == null ? "" : "\"customerId\":\"" + customerId + "\",";
        return """
                {%s"vin":"%s","registration":"BFL 1234","make":"Toyota","model":"Corolla","year":2021,"mileage":%d}
                """.formatted(customer, vin, mileage);
    }

    private static String randomVin() {
        StringBuilder vin = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            vin.append(VIN_CHARS.charAt(RANDOM.nextInt(VIN_CHARS.length())));
        }
        return vin.toString();
    }
}
