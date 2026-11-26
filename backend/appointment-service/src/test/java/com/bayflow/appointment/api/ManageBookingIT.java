package com.bayflow.appointment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.bookingservices.PublicBookingLimiter;
import com.bayflow.appointment.integration.MailClient;
import com.bayflow.appointment.integration.MailClient.Mail;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Online booking, step 3: the customer changes or cancels from the booking's private link; the
 * shop's own changes are e-mailed with a new link; the booking joins an account the customer
 * creates. Clock: Monday 16 November 2026, 9:00 in New York; the booking is Tuesday 10:00.
 */
class ManageBookingIT extends IntegrationTestBase {

    private static final Instant TUESDAY_10AM = Instant.parse("2026-11-17T15:00:00Z");
    private static final Pattern LINK = Pattern.compile("/book/manage\\?token=([A-Za-z0-9_-]{43})");

    @MockitoBean
    private WorkshopDirectory directory;

    @MockitoBean
    private MailClient mail;

    @Autowired
    private PublicBookingLimiter limiter;

    private final JdbcTemplate jdbc = ownerJdbc();
    private final UUID tenant = UUID.randomUUID();
    private final String slug = "mb-" + tenant.toString().substring(0, 8);

    @BeforeEach
    void shop() {
        limiter.reset();
        Mockito.when(directory.bySlug(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        Mockito.when(directory.bySlug(slug)).thenReturn(Optional.of(new Workshop(tenant, slug, "Lotus Auto LLC",
                "Lotus Auto", true)));
    }

    @Test
    void theCustomerMovesThenCancelsFromTheLink() throws Exception {
        String token = book("ana@example.com");
        // The e-mail carries the same private link.
        Mail booked = lastMail();
        assertThat(booked.text()).contains("http://" + slug + ".localhost:3000/book/manage?token=" + token);

        manage("", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.startsAt").value(TUESDAY_10AM.toString()))
                .andExpect(jsonPath("$.shopName").value("Lotus Auto"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.car").value("2019 Honda Civic"))
                .andExpect(jsonPath("$.canChange").value(true))
                .andExpect(jsonPath("$.reschedulesLeft").value(3))
                .andExpect(jsonPath("$.hasAccount").value(false));

        Instant wednesday = TUESDAY_10AM.plus(Duration.ofDays(1));
        reschedule(token, wednesday)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value(wednesday.toString()))
                .andExpect(jsonPath("$.reschedulesLeft").value(2));
        assertThat(lastMail().subject()).startsWith("Your booking at Lotus Auto is moved to Wednesday");
        assertThat(lastMail().text()).contains(token);
        reschedule(token, wednesday).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:same-time"));
        // Saturday: closed.
        reschedule(token, Instant.parse("2026-11-21T15:00:00Z")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:slot-taken"));

        manage("/cancel", token, "\"reason\":\"Fixed it myself\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByCustomer").value(true))
                .andExpect(jsonPath("$.canChange").value(false))
                .andExpect(jsonPath("$.blocked").value("CANCELLED"));
        assertThat(lastMail().subject()).startsWith("Cancelled: your booking at Lotus Auto");
        assertThat(lastMail().text()).doesNotContain(token);
        reschedule(token, TUESDAY_10AM).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:booking-closed"));

        // The shop sees who cancelled and why.
        mvc.perform(get("/api/v1/appointments").with(as("SERVICE_ADVISOR", UUID.randomUUID(), tenant)))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].cancelledByCustomer").value(true))
                .andExpect(jsonPath("$.content[0].cancelReason").value("Fixed it myself"))
                .andExpect(jsonPath("$.content[0].customerReschedules").value(1));
    }

    @Test
    void movesAreLimitedAndStopNearTheVisit() throws Exception {
        String token = book("ana@example.com");
        for (int day = 1; day <= 3; day++) {
            reschedule(token, TUESDAY_10AM.plus(Duration.ofDays(day))).andExpect(status().isOk());
        }
        reschedule(token, TUESDAY_10AM).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:reschedule-limit"));

        String other = book("bo@example.com");
        // An hour before the visit (inside the 2 hours' notice): call the shop.
        clock.set(TUESDAY_10AM.minus(Duration.ofHours(1)));
        manage("", other).andExpect(jsonPath("$.canChange").value(false))
                .andExpect(jsonPath("$.blocked").value("TOO_LATE"));
        manage("/cancel", other).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:too-late-to-change"));
    }

    @Test
    void aWrongLinkShowsNothing() throws Exception {
        book("ana@example.com");
        manage("", "x".repeat(43)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:booking-link-invalid"));
        manage("", "short").andExpect(status().isNotFound());
        // Changes need the web app's header.
        mvc.perform(post("/api/v1/booking/manage/cancel").header("X-Forwarded-Host", host())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + "x".repeat(43) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theShopsChangeIsEmailedWithANewLink() throws Exception {
        String token = book("ana@example.com");
        String id = jdbc.queryForObject("select id::text from bayflow.apt_appointments where tenant_id = ?",
                String.class, tenant);
        Instant thursday = TUESDAY_10AM.plus(Duration.ofDays(2));
        mvc.perform(patch("/api/v1/appointments/" + id + "/reschedule")
                        .with(advisorHere()).header("X-Forwarded-Host", host())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + thursday + "\"}"))
                .andExpect(status().isOk());

        Mail moved = lastMail();
        assertThat(moved.subject()).startsWith("Lotus Auto moved your booking to Thursday");
        Matcher link = LINK.matcher(moved.text());
        assertThat(link.find()).isTrue();
        String fresh = link.group(1);
        assertThat(fresh).isNotEqualTo(token);
        manage("", token).andExpect(status().isNotFound());
        manage("", fresh).andExpect(jsonPath("$.startsAt").value(thursday.toString()))
                // The shop's moves don't count against the customer's.
                .andExpect(jsonPath("$.reschedulesLeft").value(3));

        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .with(advisorHere()).header("X-Forwarded-Host", host()))
                .andExpect(status().isOk());
        assertThat(lastMail().subject()).startsWith("Lotus Auto cancelled your booking");
        manage("", fresh).andExpect(jsonPath("$.cancelledByCustomer").value(false));
    }

    @Test
    void theBookingJoinsTheAccountTheCustomerCreates() throws Exception {
        String token = book("ana@example.com");
        UUID ana = UUID.randomUUID();
        claim(ana, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));
        assertThat(jdbc.queryForObject("select user_id from bayflow.apt_customers where tenant_id = ?",
                UUID.class, tenant)).isEqualTo(ana);
        manage("", token).andExpect(jsonPath("$.hasAccount").value(true));
        // Claimed again: nothing changes. Someone else: refused.
        claim(ana, token).andExpect(status().isOk());
        claim(UUID.randomUUID(), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:already-claimed"));
        // The account's e-mail no longer books without signing in.
        publicBook("ana@example.com", TUESDAY_10AM.plus(Duration.ofDays(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:sign-in-to-book"));
    }

    @Test
    void aCustomerWithAProfileGetsTheCarsAndBookingsMoved() throws Exception {
        String token = book("ana.s@example.com");
        UUID ana = UUID.randomUUID();
        String profile = mvc.perform(post("/api/v1/customers/me").with(as("CUSTOMER", ana, tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Silva\",\"email\":\"ana@example.com\",\"phone\":null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String profileId = JsonPath.read(profile, "$.id");

        claim(ana, token).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(profileId));
        assertThat(jdbc.queryForObject("select count(*) from bayflow.apt_customers where tenant_id = ?",
                Integer.class, tenant)).isEqualTo(1);
        List<String> owners = jdbc.queryForList(
                "select customer_id::text from bayflow.apt_vehicles where tenant_id = ?", String.class, tenant);
        assertThat(owners).containsExactly(profileId);
        assertThat(jdbc.queryForObject("select customer_id::text from bayflow.apt_appointments where tenant_id = ?",
                String.class, tenant)).isEqualTo(profileId);
    }

    // ---- helpers ---------------------------------------------------------------

    /** An advisor signed in at this workshop's address (the token names its slug, like auth-service's). */
    private RequestPostProcessor advisorHere() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString())
                        .claim("username", "advisor-mb")
                        .claim("roles", List.of("SERVICE_ADVISOR"))
                        .claim(Tenants.CLAIM, tenant.toString())
                        .claim(Tenants.SLUG_CLAIM, slug)
                        .claim(Tenants.LOCATIONS_CLAIM, List.of("*")))
                .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_ADVISOR"));
    }

    private String host() {
        return slug + ".localhost:3000";
    }

    /** Books Tuesday 10:00 (oil change, 2019 Honda Civic); the private link's token. */
    private String book(String email) throws Exception {
        String body = publicBook(email, TUESDAY_10AM).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.manageToken");
    }

    private ResultActions publicBook(String email, Instant at) throws Exception {
        return mvc.perform(post("/api/v1/booking/appointments").header("X-Forwarded-Host", host())
                .header("X-Bayflow-Client", "web").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"location":"main","service":"oil-change","year":2019,"make":"Honda","model":"Civic",
                         "startsAt":"%s","firstName":"Ana","lastName":"Silva","email":"%s","smsConsent":false}
                        """.formatted(at, email)));
    }

    private ResultActions manage(String path, String token, String... extra) throws Exception {
        String more = extra.length == 0 ? "" : "," + String.join(",", extra);
        return mvc.perform(post("/api/v1/booking/manage" + path).header("X-Forwarded-Host", host())
                .header("X-Bayflow-Client", "web").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"" + more + "}"));
    }

    private ResultActions reschedule(String token, Instant at) throws Exception {
        return manage("/reschedule", token, "\"startsAt\":\"" + at + "\"");
    }

    private ResultActions claim(UUID user, String token) throws Exception {
        return mvc.perform(post("/api/v1/customers/me/claim").with(as("CUSTOMER", user, tenant))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private Mail lastMail() {
        ArgumentCaptor<Mail> sent = ArgumentCaptor.forClass(Mail.class);
        Mockito.verify(mail, Mockito.atLeastOnce()).send(ArgumentMatchers.eq(tenant), sent.capture());
        return sent.getValue();
    }
}
