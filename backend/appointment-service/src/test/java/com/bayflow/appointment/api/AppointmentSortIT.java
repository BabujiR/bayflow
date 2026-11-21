package com.bayflow.appointment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/** The appointment list's order: upcoming (default), latest, earliest or most recently booked. */
class AppointmentSortIT extends IntegrationTestBase {

    private static final Instant DAY1 = Instant.parse("2026-11-17T15:00:00Z");
    private static final Instant DAY2 = DAY1.plus(Duration.ofDays(1));
    private static final Instant DAY3 = DAY1.plus(Duration.ofDays(2));

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());
    private final RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());

    private String vehicleId;
    private String first;
    private String second;
    private String third;

    /** Three bookings, made in the order day 3, day 1, day 2; then day 1 passes. */
    @BeforeEach
    void bookings() throws Exception {
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sam Sort\",\"email\":\"sam@example.com\"}")).andExpect(status().isCreated());
        String vin = ("JS" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .replaceAll("[IOQ]", "1").substring(0, 17);
        vehicleId = JsonPath.read(mvc.perform(post("/api/v1/vehicles").with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"SRT 1","make":"Mazda","model":"3","year":2019,"mileage":0}
                                """.formatted(vin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        third = book(DAY3);
        clock.advance(Duration.ofMinutes(1));
        first = book(DAY1);
        clock.advance(Duration.ofMinutes(1));
        second = book(DAY2);
        // Day 1's appointment (an hour long) is over.
        clock.set(DAY1.plus(Duration.ofHours(2)));
    }

    @Test
    void upcomingFirstThenThePastIsTheDefault() throws Exception {
        assertThat(ids(null)).containsExactly(second, third, first);
        assertThat(ids("upcoming")).containsExactly(second, third, first);
    }

    @Test
    void byAppointmentTimeEitherWay() throws Exception {
        assertThat(ids("earliest")).containsExactly(first, second, third);
        assertThat(ids("latest")).containsExactly(third, second, first);
        assertThat(ids("LATEST")).containsExactly(third, second, first);
    }

    @Test
    void mostRecentlyBookedFirst() throws Exception {
        assertThat(ids("recent")).containsExactly(second, first, third);
    }

    @Test
    void oneDayDefaultsToTimeOrder() throws Exception {
        mvc.perform(list(null).param("date", "2026-11-18"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second));
    }

    @Test
    void pagesNeitherRepeatNorSkip() throws Exception {
        for (String sort : new String[] { "upcoming", "latest", "earliest", "recent" }) {
            List<String> paged = new ArrayList<>();
            for (int page = 0; page < 3; page++) {
                String body = mvc.perform(list(sort).param("size", "1").param("page", String.valueOf(page)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                paged.add(JsonPath.read(body, "$.content[0].id"));
            }
            assertThat(paged).as(sort).containsExactlyElementsOf(ids(sort));
        }
    }

    @Test
    void anUnknownSortIsRefused() throws Exception {
        mvc.perform(list("cheapest")).andExpect(status().isBadRequest());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private String book(Instant at) throws Exception {
        String body = mvc.perform(post("/api/v1/appointments").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"%s\",\"scheduledAt\":\"%s\",\"serviceType\":\"OIL_CHANGE\"}"
                                .formatted(vehicleId, at)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MockHttpServletRequestBuilder list(String sort) {
        MockHttpServletRequestBuilder request = get("/api/v1/appointments").with(advisor).param("vehicleId", vehicleId);
        return sort == null ? request : request.param("sort", sort);
    }

    private List<String> ids(String sort) throws Exception {
        String body = mvc.perform(list(sort)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.content[*].id");
    }
}
