package com.bayflow.appointment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.bookingservices.PublicBookingLimiter;
import com.bayflow.appointment.integration.MailClient;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;

/**
 * Online booking, step 2: the public booking page (no sign-in). The clock starts on Monday
 * 16 November 2026, 9:00 in New York; the shop's location is "Main" there, open Mon-Fri 8-5 by
 * default, 30-minute slots, 2 cars per slot, 2 hours' notice.
 */
class OnlineBookingIT extends IntegrationTestBase {

    /** Monday 11:00 New York: the first slot after 2 hours' notice. */
    private static final String FIRST_SLOT = "2026-11-16T16:00:00Z";

    @MockitoBean
    private WorkshopDirectory directory;

    @MockitoBean
    private MailClient mail;

    @Autowired
    private PublicBookingLimiter limiter;

    private final JdbcTemplate jdbc = ownerJdbc();
    private final UUID tenant = UUID.randomUUID();
    private final String slug = "bk-" + tenant.toString().substring(0, 8);

    @BeforeEach
    void shop() {
        limiter.reset();
        Mockito.when(directory.bySlug(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        Mockito.when(directory.bySlug(slug)).thenReturn(Optional.of(new Workshop(tenant, slug, "Lotus Auto LLC",
                "Lotus Auto", true)));
    }

    @Test
    void theShopsOptionsAndFreeTimes() throws Exception {
        publicGet("/api/v1/booking/options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Lotus Auto"))
                .andExpect(jsonPath("$.locations", hasSize(1)))
                .andExpect(jsonPath("$.locations[0].code").value("main"))
                .andExpect(jsonPath("$.locations[0].timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.services[0].code").value("oil-change"));

        publicGet("/api/v1/booking/availability?location=main&from=2026-11-16&days=7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-16"))
                // Not before 11:00 (2 hours' notice); 30-minute slots until 4:30 pm.
                .andExpect(jsonPath("$.days[0].slots[0]").value(FIRST_SLOT))
                .andExpect(jsonPath("$.days[0].slots", hasSize(12)))
                .andExpect(jsonPath("$.days[1].slots", hasSize(18)))
                // Saturday and Sunday: closed.
                .andExpect(jsonPath("$.days[5].slots", hasSize(0)))
                .andExpect(jsonPath("$.days[6].slots", hasSize(0)));
    }

    @Test
    void aGuestBooksWithoutAnAccountAndTheShopSeesIt() throws Exception {
        book(Map.of("service", "oil-change", "year", 2019, "make", "Honda", "model", "Civic",
                "phone", "555 010 0199", "smsConsent", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value(FIRST_SLOT))
                .andExpect(jsonPath("$.serviceName").value("Oil change"))
                .andExpect(jsonPath("$.locationName").value("Main"))
                .andExpect(jsonPath("$.emailed").value(true));

        Map<String, Object> row = jdbc.queryForMap("""
                select a.booked_online, a.service_name, a.service_type, a.status, a.sms_consent_at,
                       c.user_id, c.name, c.email, c.phone, v.vin, v.registration, v.make, v.model_year
                from bayflow.apt_appointments a
                join bayflow.apt_customers c on c.id = a.customer_id
                join bayflow.apt_vehicles v on v.id = a.vehicle_id
                where a.tenant_id = ?""", tenant);
        assertThat(row.get("booked_online")).isEqualTo(true);
        assertThat(row.get("service_name")).isEqualTo("Oil change");
        assertThat(row.get("service_type")).isEqualTo("OIL_CHANGE");
        assertThat(row.get("status")).isEqualTo("BOOKED");
        assertThat(row.get("sms_consent_at")).isNotNull();
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("name")).isEqualTo("Ana Silva");
        assertThat(row.get("email")).isEqualTo("ana@example.com");
        assertThat(row.get("vin")).isNull();
        assertThat(row.get("make")).isEqualTo("Honda");
        assertThat(row.get("model_year")).isEqualTo(2019);
        Mockito.verify(mail).send(ArgumentMatchers.eq(tenant), ArgumentMatchers.argThat(m ->
                m.to().equals("ana@example.com") && m.subject().startsWith("You're booked at Lotus Auto")
                        && m.text().contains("2019 Honda Civic")));

        // Staff see it as an online booking.
        mvc.perform(get("/api/v1/appointments").with(as("SERVICE_ADVISOR", UUID.randomUUID(), tenant)))
                .andExpect(jsonPath("$.content[0].bookedOnline").value(true))
                .andExpect(jsonPath("$.content[0].serviceName").value("Oil change"));

        // Asking again with the same e-mail and car: the same customer and car.
        book(Map.of("year", 2019, "make", "honda", "model", "civic", "description", "Squeaky brakes",
                "startsAt", "2026-11-17T15:00:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Something else"));
        assertThat(jdbc.queryForObject("select count(*) from bayflow.apt_customers where tenant_id = ?",
                Integer.class, tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from bayflow.apt_vehicles where tenant_id = ?",
                Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void aFullSlotIsNoLongerOffered() throws Exception {
        UUID location = tenant;
        mvc.perform(put("/api/v1/booking-services/hours/" + location)
                        .with(as("ADMIN", UUID.randomUUID(), tenant)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":[{"opens":"08:00","closes":"17:00"},{"opens":"08:00","closes":"17:00"},
                                 {"opens":"08:00","closes":"17:00"},{"opens":"08:00","closes":"17:00"},
                                 {"opens":"08:00","closes":"12:00"},{"opens":"09:00","closes":"12:00"},{}],
                                 "slotMinutes":60,"carsPerSlot":1,"minNoticeMinutes":120,"maxDaysAhead":30,
                                 "version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[5].opens").value("09:00"))
                .andExpect(jsonPath("$.days[6].opens").value(org.hamcrest.Matchers.nullValue()));

        book(Map.of("year", 2020, "make", "Ford", "model", "Focus", "service", "brakes"))
                .andExpect(status().isCreated());
        book(Map.of("vin", "1HGCV1F3XLA012345", "service", "brakes", "email", "bo@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:slot-taken"));
        publicGet("/api/v1/booking/availability?location=main&from=2026-11-16&days=7")
                .andExpect(jsonPath("$.days[0].slots", not(hasItem(FIRST_SLOT))))
                // Saturday 9 to 12 in hour slots.
                .andExpect(jsonPath("$.days[5].slots", hasSize(3)));

        // Stale settings are refused.
        mvc.perform(put("/api/v1/booking-services/hours/" + location)
                        .with(as("ADMIN", UUID.randomUUID(), tenant)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":[{},{},{},{},{},{},{}],"slotMinutes":30,"carsPerSlot":2,
                                 "minNoticeMinutes":0,"maxDaysAhead":30,"version":7}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void whatTheBookingNeeds() throws Exception {
        book(Map.of("vin", "NOT-A-VIN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-vin"));
        book(Map.of("make", "Honda")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vehicle-required"));
        book(Map.of("year", 2019, "make", "Honda", "model", "Civic")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:description-required"));
        book(Map.of("year", 2019, "make", "Honda", "model", "Civic", "service", "oil-change",
                "startsAt", "2026-11-16T15:00:00Z"))
                .andExpect(status().isConflict()); // inside the 2 hours' notice
        book(Map.of("year", 2019, "make", "Honda", "model", "Civic", "service", "oil-change",
                "startsAt", "2026-11-21T15:00:00Z"))
                .andExpect(status().isConflict()); // Saturday: closed
        // Without the web app's header, at an unknown address, or from a bot: nothing booked.
        mvc.perform(post("/api/v1/booking/appointments").header("X-Forwarded-Host", slug + ".localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("location", "main",
                                "startsAt", FIRST_SLOT, "firstName", "Ana", "lastName", "Silva",
                                "email", "ana@example.com", "service", "oil-change", "vin", "1HGCV1F3XLA012345"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/booking/options").header("X-Forwarded-Host", "nobody.localhost:3000"))
                .andExpect(status().isNotFound());
        book(Map.of("year", 2019, "make", "Honda", "model", "Civic", "service", "oil-change",
                "website", "http://spam.example"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from bayflow.apt_appointments where tenant_id = ?",
                Integer.class, tenant)).isZero();

        Mockito.when(directory.bySlug(slug)).thenReturn(Optional.of(new Workshop(tenant, slug, "Lotus Auto LLC",
                null, false)));
        publicGet("/api/v1/booking/options").andExpect(status().isForbidden());
    }

    @Test
    void aCarBookedWithoutVinOrPlateGetsThemAtCheckIn() throws Exception {
        book(Map.of("year", 2019, "make", "Honda", "model", "Civic", "service", "oil-change"))
                .andExpect(status().isCreated());
        String id = jdbc.queryForObject("select id::text from bayflow.apt_appointments where tenant_id = ?",
                String.class, tenant);
        var advisor = as("SERVICE_ADVISOR", UUID.randomUUID(), tenant);
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/arrive").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":42000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vehicle-details-required"));
        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mileage\":42000,\"vin\":\"1hgcv1f3xla012345\",\"registration\":\"8xyz214\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.vehicle.vin").value("1HGCV1F3XLA012345"))
                .andExpect(jsonPath("$.vehicle.registration").value("8XYZ214"));
    }

    // ---- helpers ---------------------------------------------------------------

    private ResultActions publicGet(String path) throws Exception {
        return mvc.perform(get(path).header("X-Forwarded-Host", slug + ".localhost:3000"));
    }

    /** A booking with sensible defaults; {@code fields} adds or overrides. */
    private ResultActions book(Map<String, Object> fields) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of("location", "main",
                "startsAt", FIRST_SLOT, "firstName", "Ana", "lastName", "Silva", "email", "ana@example.com",
                "smsConsent", false));
        body.putAll(fields);
        MockHttpServletRequestBuilder request = post("/api/v1/booking/appointments")
                .header("X-Forwarded-Host", slug + ".localhost:3000")
                .header("X-Bayflow-Client", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return mvc.perform(request);
    }

    private static String json(Map<String, Object> map) {
        StringBuilder out = new StringBuilder("{");
        map.forEach((k, v) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append('"').append(k).append("\":");
            out.append(v instanceof String s ? "\"" + s.replace("\"", "\\\"") + "\"" : String.valueOf(v));
        });
        return out.append('}').toString();
    }
}
