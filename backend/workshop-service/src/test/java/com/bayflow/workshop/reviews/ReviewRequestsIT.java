package com.bayflow.workshop.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.AuthServiceClient.LocationProfile;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.bayflow.workshop.integration.AuthServiceClient.ReviewSettings;
import com.bayflow.workshop.integration.AuthServiceClient.WorkshopProfile;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.jayway.jsonpath.JsonPath;

/** Google review requests: scheduled when an invoice is paid in full, then sent or skipped. */
class ReviewRequestsIT extends IntegrationTestBase {

    private static final String REVIEW_URL = "https://g.page/r/lotus-auto/review";
    private static final Pattern STOP_TOKEN = Pattern.compile("/reviews/stop\\?token=([A-Za-z0-9_-]{43})");

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam");
    /** A customer of their own in each test: the 90-day rule and "stop" go by e-mail address. */
    private final String email = "pat-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    @Autowired
    private ReviewRequests reviews;

    @BeforeEach
    void shop() {
        Mockito.doReturn(new BillingContact(UUID.randomUUID(), "Pat Lee", email, null, "Toyota", "Corolla", 2018))
                .when(billingContacts).get(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(new WorkshopProfile("lotus", "Lotus Auto LLC", "Lotus Auto", "555-0100", "desk@example.com",
                null)).when(authService).workshop(ArgumentMatchers.any());
        asking(new ReviewSettings(true, REVIEW_URL, "TWO_HOURS"));
    }

    @Test
    void aPaidInvoiceIsAskedForAReviewLaterAndTheCustomerCanStopThem() throws Exception {
        String invoice = paidInvoice();
        Instant paidAt = Instant.now();

        String scheduled = body(reviewOf(invoice).andExpect(jsonPath("$.status").value("SCHEDULED")));
        Instant due = Instant.parse(JsonPath.read(scheduled, "$.dueAt"));
        assertThat(due).isBetween(paidAt.plus(Duration.ofMinutes(110)), paidAt.plus(Duration.ofMinutes(130)));
        // Not due yet: nothing goes out.
        reviews.sendDue();
        Mockito.verify(authService, Mockito.never()).send(ArgumentMatchers.any(), ArgumentMatchers.any());

        makeDue(invoice);
        reviews.sendDue();
        reviewOf(invoice).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentTo").value(email))
                .andExpect(jsonPath("$.sentBy").value("automatic"));
        Mail mail = lastMail();
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.senderName()).isEqualTo("Lotus Auto");
        assertThat(mail.subject()).contains("Lotus Auto");
        assertThat(mail.html()).contains(REVIEW_URL).contains("How did we do, Pat?").contains("2018 Toyota Corolla");

        // The stop link: no more requests to this address, even when staff ask.
        Matcher token = STOP_TOKEN.matcher(mail.text());
        assertThat(token.find()).isTrue();
        stop(token.group(1)).andExpect(status().isOk()).andExpect(jsonPath("$.shopName").value("Lotus Auto"));
        stop(token.group(1)).andExpect(status().isOk());
        stop("x".repeat(43)).andExpect(status().isNotFound());
        String next = paidInvoice();
        sendNow(next).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:review-opted-out"));
        makeDue(next);
        reviews.sendDue();
        reviewOf(next).andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.reason").value("The customer asked not to get review requests"));
    }

    @Test
    void aCustomerIsAskedAtMostOnceIn90DaysUnlessStaffAsk() throws Exception {
        String first = paidInvoice();
        makeDue(first);
        reviews.sendDue();
        reviewOf(first).andExpect(jsonPath("$.status").value("SENT"));

        String second = paidInvoice();
        makeDue(second);
        reviews.sendDue();
        reviewOf(second).andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.reason", startsWith("Asked on ")));

        // Staff can still ask (they chose to); once is enough.
        sendNow(second).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentBy").value("sam"));
        sendNow(second).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:review-already-requested"));
    }

    @Test
    void staffCanStopAScheduledRequest() throws Exception {
        String invoice = paidInvoice();
        cancel(invoice).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reason").value("Not sent: sam chose not to"));
        cancel(invoice).andExpect(status().isConflict());
        makeDue(invoice);
        reviews.sendDue();
        reviewOf(invoice).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aLocationThatDoesNotAskSchedulesNothing() throws Exception {
        asking(new ReviewSettings(false, null, "NEXT_MORNING"));
        String invoice = paidInvoice();
        reviewOf(invoice).andExpect(jsonPath("$.status").value("NONE"));
        sendNow(invoice).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:no-review-link"));

        // Turned off after it was scheduled: skipped when due.
        asking(new ReviewSettings(true, REVIEW_URL, "NEXT_MORNING"));
        String later = paidInvoice();
        reviewOf(later).andExpect(jsonPath("$.status").value("SCHEDULED"));
        asking(new ReviewSettings(false, REVIEW_URL, "NEXT_MORNING"));
        makeDue(later);
        reviews.sendDue();
        reviewOf(later).andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.reason").value("Review requests are off at this location"));
    }

    @Test
    void onlyAFullyPaidInvoiceIsAskedAbout() throws Exception {
        String invoice = issuedInvoice();
        reviewOf(invoice).andExpect(jsonPath("$.status").value("NONE"));
        sendNow(invoice).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invoice-not-paid"));
        mvc.perform(get("/api/v1/invoices/" + invoice + "/review-request")
                        .with(as("MECHANIC", UUID.randomUUID().toString(), "mike")))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void asking(ReviewSettings settings) {
        Mockito.doReturn(new LocationProfile("Main", "America/New_York", "555-0101", null, null, settings))
                .when(authService).location(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private void makeDue(String invoiceId) {
        ownerJdbc().update("update bayflow.ws_review_requests set due_at = now() - interval '1 minute'"
                + " where invoice_id = ?::uuid and status = 'SCHEDULED'", invoiceId);
    }

    private Mail lastMail() {
        ArgumentCaptor<Mail> sent = ArgumentCaptor.forClass(Mail.class);
        Mockito.verify(authService, Mockito.atLeastOnce()).send(ArgumentMatchers.any(), sent.capture());
        return sent.getValue();
    }

    private ResultActions reviewOf(String invoiceId) throws Exception {
        return mvc.perform(get("/api/v1/invoices/" + invoiceId + "/review-request").with(advisor))
                .andExpect(status().isOk());
    }

    private ResultActions sendNow(String invoiceId) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + invoiceId + "/review-request/send").with(advisor));
    }

    private ResultActions cancel(String invoiceId) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + invoiceId + "/review-request/cancel").with(advisor));
    }

    private ResultActions stop(String token) throws Exception {
        return mvc.perform(post("/api/v1/review-requests/stop").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    /** An issued invoice for a completed job, then paid in full. */
    private String paidInvoice() throws Exception {
        String id = issuedInvoice();
        String total = JsonPath.read(body(mvc.perform(get("/api/v1/invoices/" + id).with(advisor))), "$.total")
                .toString();
        mvc.perform(post("/api/v1/invoices/" + id + "/payments").with(advisor)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amount\":" + total + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        return id;
    }

    private String issuedInvoice() throws Exception {
        String card = completedJob();
        String id = JsonPath.read(body(mvc.perform(post("/api/v1/job-cards/" + card + "/invoice").with(advisor))
                .andExpect(status().isCreated())), "$.id");
        mvc.perform(post("/api/v1/invoices/" + id + "/issue").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        return id;
    }

    /** A job card taken to COMPLETED in the default workshop's Main location. */
    private String completedJob() throws Exception {
        UUID customerId = UUID.randomUUID();
        String card = JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"2T1BURHE2JC034501","registration":"RV 1",
                                 "customerUserId":"%s","customerName":"Pat Lee","mileage":40000,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), customerId,
                                Tenants.DEFAULT_TENANT_ID)))
                .andExpect(status().isCreated())), "$.id");
        RequestPostProcessor tech = as("MECHANIC", UUID.randomUUID().toString(), "mike");
        act(tech, card, "findings", "{\"description\":\"Oil due\",\"severity\":\"LOW\"}");
        String item = JsonPath.read(body(act(advisor, card, "recommendations",
                "{\"description\":\"Oil change\",\"estimatedPartsCost\":40,\"estimatedLabourHours\":0.5}")),
                "$.recommendations[0].id");
        act(advisor, card, "submit-for-approval", null);
        act(advisor, card, "recommendations/" + item + "/approve", null);
        act(tech, card, "start", null);
        // Completing needs the work recorded on each approved item.
        act(tech, card, "work-logs", "{\"recommendationId\":\"%s\",\"description\":\"Changed\"}".formatted(item));
        act(tech, card, "parts", """
                {"recommendationId":"%s","partNumber":"OIL-5","name":"Oil","quantity":1,"unitPrice":40}
                """.formatted(item));
        act(tech, card, "labour", "{\"recommendationId\":\"%s\",\"hours\":0.5}".formatted(item));
        act(tech, card, "complete", null);
        return card;
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
