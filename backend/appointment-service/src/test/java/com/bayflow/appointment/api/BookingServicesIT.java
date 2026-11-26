package com.bayflow.appointment.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/** Online booking, step 1: the shop's own list of bookable services (Settings → Online booking). */
class BookingServicesIT extends IntegrationTestBase {

    private final UUID tenant = UUID.randomUUID();
    private final RequestPostProcessor admin = as("ADMIN", UUID.randomUUID(), tenant);

    @Test
    void aNewShopStartsWithCommonServicesWithoutPrices() throws Exception {
        call(get("/api/v1/booking-services"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].code", contains("oil-change", "routine-service", "brakes", "tires",
                        "inspection", "diagnostic")))
                .andExpect(jsonPath("$[0].name").value("Oil change"))
                .andExpect(jsonPath("$[0].priceKind").value("NONE"))
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[0].active").value(true));
        // Only once: a second visit doesn't add them again.
        call(get("/api/v1/booking-services"), admin).andExpect(jsonPath("$", hasSize(6)));
        // Advisors (and managers) read the list; customers don't.
        call(get("/api/v1/booking-services"), as("SERVICE_ADVISOR", UUID.randomUUID(), tenant))
                .andExpect(status().isOk());
        call(get("/api/v1/booking-services"), as("CUSTOMER", UUID.randomUUID(), tenant))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminAddsChangesHidesAndReordersServices() throws Exception {
        call(get("/api/v1/booking-services"), admin);
        String created = body(send(post("/api/v1/booking-services"), admin, """
                {"name":"Wheel alignment","description":"Straight steering, even tire wear","priceKind":"FROM",
                 "price":89.00,"durationMinutes":60,"active":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("wheel-alignment"))
                .andExpect(jsonPath("$.price").value(89.0))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.sortOrder").value(7)));
        String id = JsonPath.read(created, "$.id");

        // The same name again gets its own link name.
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Oil change","priceKind":"FIXED","price":54,"active":true}""")
                .andExpect(jsonPath("$.code").value("oil-change-2"));

        // A quote has no amount, whatever is sent; hiding keeps it in the list.
        send(put("/api/v1/booking-services/" + id), admin, """
                {"name":"Wheel alignment","priceKind":"QUOTE","price":89,"active":false,"version":0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceKind").value("QUOTE"))
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").value("wheel-alignment"));
        send(put("/api/v1/booking-services/" + id), admin, """
                {"name":"Stale","priceKind":"NONE","active":true,"version":0}""")
                .andExpect(status().isConflict());

        List<String> ids = JsonPath.read(body(call(get("/api/v1/booking-services"), admin)), "$[*].id");
        List<String> reversed = new java.util.ArrayList<>(ids);
        java.util.Collections.reverse(reversed);
        send(put("/api/v1/booking-services/order"), admin, "{\"ids\":[\"" + String.join("\",\"", reversed) + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reversed.get(0)))
                .andExpect(jsonPath("$[0].sortOrder").value(1));
        send(put("/api/v1/booking-services/order"), admin, "{\"ids\":[\"" + reversed.get(0) + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-order"));
    }

    @Test
    void pricesTimesAndLocationsAreChecked() throws Exception {
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Tune-up","priceKind":"FROM","active":true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-price"));
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Tune-up","priceKind":"FIXED","price":-1,"active":true}""")
                .andExpect(status().isBadRequest());
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Tune-up","priceKind":"NONE","durationMinutes":2,"active":true}""")
                .andExpect(status().isBadRequest());
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Tune-up","priceKind":"NONE","locationId":"%s","active":true}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-location"));
        // The organization's own location ("Main", id = tenant id) is fine.
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Tune-up","priceKind":"NONE","locationId":"%s","active":true}""".formatted(tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(tenant.toString()));
        // Only the admin changes the list.
        send(post("/api/v1/booking-services"), as("SERVICE_ADVISOR", UUID.randomUUID(), tenant), """
                {"name":"Sneaky","priceKind":"NONE","active":true}""")
                .andExpect(status().isForbidden());
    }

    @Test
    void eachShopHasItsOwnList() throws Exception {
        send(post("/api/v1/booking-services"), admin, """
                {"name":"Detailing","priceKind":"FREE","active":true}""")
                .andExpect(status().isCreated());
        UUID other = UUID.randomUUID();
        call(get("/api/v1/booking-services"), as("ADMIN", UUID.randomUUID(), other))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.name == 'Detailing')]").isEmpty());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, RequestPostProcessor who) throws Exception {
        return mvc.perform(request.with(who));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor who, String json)
            throws Exception {
        return mvc.perform(request.with(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
