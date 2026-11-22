package com.bayflow.workshop.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.bayflow.workshop.integration.WorkshopSettingsClient.LocationBilling;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;
import com.jayway.jsonpath.JsonPath;

/**
 * Invoicing, phase R2a. The location charges 8.875 % tax on parts and fees (none on labour),
 * 5 % of labour as shop supplies (capped at 35.00) and a 3.50 disposal fee, due in 15 days.
 * The job: 2 brake pads at 45.25 (90.50) and 1.25 h labour at 95.00 (118.75).
 */
class InvoiceIT extends IntegrationTestBase {

    private static final UUID NORTH = UUID.fromString("0d0d0d0d-3333-4000-8000-0000000000e3");

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mike");
    private final RequestPostProcessor admin = as("ADMIN", UUID.randomUUID().toString(), "owner");

    @BeforeEach
    void taxedLocation() {
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("95.00"), "USD", new LocationBilling(
                new BigDecimal("8.875"), BigDecimal.ZERO, new BigDecimal("8.875"), new BigDecimal("5"),
                new BigDecimal("35.00"), new BigDecimal("3.50"), 15, "12-month parts and labour warranty")))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(new BillingContact(UUID.randomUUID(), "Ana Silva", "ana@example.com", "+1 555 0100",
                "Toyota", "Corolla", 2019)).when(billingContacts).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void aCompletedJobBecomesADraftWithTaxAndFees() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);

        createDraft(job.cardId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value(nullValue()))
                .andExpect(jsonPath("$.customerName").value("Ana Silva"))
                .andExpect(jsonPath("$.customerEmail").value("ana@example.com"))
                .andExpect(jsonPath("$.vehicleDescription").value("2019 Toyota Corolla"))
                .andExpect(jsonPath("$.lines", hasSize(4)))
                .andExpect(jsonPath("$.lines[0].type").value("PART"))
                .andExpect(jsonPath("$.lines[0].lineTotal", closeTo(90.50, 0.001)))
                .andExpect(jsonPath("$.lines[1].type").value("LABOUR"))
                .andExpect(jsonPath("$.lines[1].lineTotal", closeTo(118.75, 0.001)))
                // 5 % of 118.75 = 5.94; then the disposal fee.
                .andExpect(jsonPath("$.lines[2].feeCode").value("SHOP_SUPPLIES"))
                .andExpect(jsonPath("$.lines[2].lineTotal", closeTo(5.94, 0.001)))
                .andExpect(jsonPath("$.lines[3].lineTotal", closeTo(3.50, 0.001)))
                .andExpect(jsonPath("$.subtotal", closeTo(218.69, 0.001)))
                // 8.875 % of 90.50 = 8.03; of 9.44 fees = 0.84; labour untaxed.
                .andExpect(jsonPath("$.taxes[0].tax", closeTo(8.03, 0.001)))
                .andExpect(jsonPath("$.taxes[1].tax", closeTo(0.00, 0.001)))
                .andExpect(jsonPath("$.taxes[2].tax", closeTo(0.84, 0.001)))
                .andExpect(jsonPath("$.total", closeTo(227.56, 0.001)))
                .andExpect(jsonPath("$.balanceDue", closeTo(227.56, 0.001)))
                .andExpect(jsonPath("$.footer").value("12-month parts and labour warranty"));

        createDraft(job.cardId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invoice-exists"));
        mvc.perform(get("/api/v1/job-cards/" + job.cardId() + "/invoice").with(advisor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void onlyACompletedJobCanBeInvoicedAndMechanicsCannot() throws Exception {
        String open = openCard(Tenants.DEFAULT_TENANT_ID, null);
        createDraft(open).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:job-not-completed"));
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        mvc.perform(post("/api/v1/job-cards/" + job.cardId() + "/invoice").with(mechanic))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoices").with(mechanic)).andExpect(status().isForbidden());
    }

    @Test
    void theAdvisorRepricesEntersCostsAndAddsADiscount() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String draft = body(createDraft(job.cardId()));
        String id = JsonPath.read(draft, "$.id");
        String part = JsonPath.read(draft, "$.lines[0].id");
        String labour = JsonPath.read(draft, "$.lines[1].id");
        String supplies = JsonPath.read(draft, "$.lines[2].id");
        String disposal = JsonPath.read(draft, "$.lines[3].id");
        int version = JsonPath.read(draft, "$.version");

        String edited = """
                {"version":%d,"notes":"Thanks for choosing us","lines":[
                 {"id":"%s","description":"Front brake pads","partNumber":"BP-220","quantity":2,"unitPrice":45.25,"unitCost":30},
                 {"id":"%s","description":"Labour: brakes","quantity":1.25,"unitPrice":100},
                 {"id":"%s","description":"Shop supplies","quantity":1,"unitPrice":0},
                 {"id":"%s","description":"Disposal fee","quantity":1,"unitPrice":3.50},
                 {"type":"DISCOUNT","description":"Loyalty discount","quantity":1,"unitPrice":10,"taxCategory":"LABOUR"}]}
                """.formatted(version, part, labour, supplies, disposal);
        mvc.perform(put("/api/v1/invoices/" + id).with(advisor).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labourTotal", closeTo(125.00, 0.001)))
                // Shop supplies follow the new labour total: 5 % of 125.00.
                .andExpect(jsonPath("$.lines[2].lineTotal", closeTo(6.25, 0.001)))
                .andExpect(jsonPath("$.lines[4].lineTotal", closeTo(-10.00, 0.001)))
                .andExpect(jsonPath("$.discountTotal", closeTo(10.00, 0.001)))
                // 90.50 + 125.00 + 9.75 − 10.00
                .andExpect(jsonPath("$.subtotal", closeTo(215.25, 0.001)))
                .andExpect(jsonPath("$.partsProfit.partsCost", closeTo(60.00, 0.001)))
                .andExpect(jsonPath("$.partsProfit.profit", closeTo(30.50, 0.001)))
                .andExpect(jsonPath("$.notes").value("Thanks for choosing us"));

        // Parts and labour from the job card can't be dropped; a stale version is a conflict.
        String withoutLabour = """
                {"version":%d,"lines":[{"id":"%s","description":"Pads","quantity":2,"unitPrice":45.25}]}
                """.formatted(version + 1, part);
        mvc.perform(put("/api/v1/invoices/" + id).with(advisor).contentType(MediaType.APPLICATION_JSON)
                        .content(withoutLabour))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:job-card-line-removed"));
        mvc.perform(put("/api/v1/invoices/" + id).with(advisor).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:concurrent-update"));
    }

    @Test
    void issuingNumbersAndLocksThenPaymentsSettleIt() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String id = JsonPath.read(body(createDraft(job.cardId())), "$.id");

        String issued = body(issue(id, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.number").value(matchesPattern("INV-\\d+")))
                .andExpect(jsonPath("$.issuedBy").value("sam")));
        Instant issuedAt = Instant.parse(JsonPath.read(issued, "$.issuedAt"));
        assertThat(Instant.parse(JsonPath.<String>read(issued, "$.dueAt")))
                .isEqualTo(issuedAt.plus(Duration.ofDays(15)));
        issue(id, 1).andExpect(status().isConflict());

        pay(advisor, id, "100.00", "key-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue", closeTo(127.56, 0.001)));
        // A retried request with the same key is recorded once.
        pay(advisor, id, "100.00", "key-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.amountPaid", closeTo(100.00, 0.001)));
        pay(advisor, id, "200.00", "key-2").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:overpayment"));
        pay(advisor, id, "127.56", "key-3").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.balanceDue", closeTo(0.00, 0.001)));

        // Refunds: managers and admins only, never more than was paid.
        pay(advisor, id, "-10.00", "key-4").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:refund-not-allowed"));
        pay(admin, id, "-300.00", "key-5").andExpect(status().isConflict());
        pay(admin, id, "-10.00", "key-6").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue", closeTo(10.00, 0.001)));

        // Paid money must be refunded before voiding; an issued invoice can't be edited.
        mvc.perform(post("/api/v1/invoices/" + id + "/void").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong customer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invoice-has-payments"));
        mvc.perform(put("/api/v1/invoices/" + id).with(advisor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":99,\"lines\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aVoidedInvoiceKeepsItsNumberAndTheJobCanBeInvoicedAgain() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String id = JsonPath.read(body(createDraft(job.cardId())), "$.id");
        String number = JsonPath.read(body(issue(id, 0)), "$.number");

        mvc.perform(post("/api/v1/invoices/" + id + "/void").with(advisor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong customer\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/invoices/" + id + "/void").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.voidReason").value("Wrong customer"));

        createDraft(job.cardId()).andExpect(status().isCreated());
    }

    @Test
    void aDraftCanBeDiscarded() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String id = JsonPath.read(body(createDraft(job.cardId())), "$.id");
        mvc.perform(delete("/api/v1/invoices/" + id).with(advisor)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/invoices/" + id).with(advisor)).andExpect(status().isNotFound());
        createDraft(job.cardId()).andExpect(status().isCreated());
    }

    @Test
    void numbersNeverRepeatOrSkipWhenIssuedTogether() throws Exception {
        UUID tenant = UUID.randomUUID();
        RequestPostProcessor desk = staff(tenant, "SERVICE_ADVISOR", "desk", "*");
        List<String> drafts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Job job = completedJob(tenant);
            drafts.add(JsonPath.read(body(mvc.perform(post("/api/v1/job-cards/" + job.cardId() + "/invoice")
                    .with(desk)).andExpect(status().isCreated())), "$.id"));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            try {
                List<Callable<String>> issues = drafts.stream().<Callable<String>>map(id -> () -> JsonPath.read(body(
                        mvc.perform(post("/api/v1/invoices/" + id + "/issue").with(desk)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                            .andExpect(status().isOk())), "$.number")).toList();
                List<Long> numbers = new ArrayList<>();
                for (Future<String> number : pool.invokeAll(issues)) {
                    numbers.add(Long.parseLong(number.get().substring("INV-".length())));
                }
                // A new organization starts at 1001: six issues take 1001–1006, each once.
                assertThat(numbers).containsExactlyInAnyOrder(1001L, 1002L, 1003L, 1004L, 1005L, 1006L);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void customersSeeTheirOwnIssuedInvoicesWithoutCosts() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String id = JsonPath.read(body(createDraft(job.cardId())), "$.id");
        RequestPostProcessor customer = as("CUSTOMER", job.customerId().toString(), "ana");

        // A draft is the workshop's business.
        mvc.perform(get("/api/v1/invoices/" + id).with(customer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/job-cards/" + job.cardId() + "/invoice").with(customer))
                .andExpect(status().isNotFound());

        issue(id, 0).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invoices/" + id).with(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitCost").value(nullValue()))
                .andExpect(jsonPath("$.partsProfit").value(nullValue()));
        mvc.perform(get("/api/v1/invoices").with(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/v1/invoices/" + id).with(as("CUSTOMER", UUID.randomUUID().toString(), "other")))
                .andExpect(status().isNotFound());
        // Customers never record payments.
        pay(customer, id, "10.00", "c-1").andExpect(status().isForbidden());
    }

    @Test
    void staffSeeOnlyTheirLocationsInvoices() throws Exception {
        Job job = completedJob(Tenants.DEFAULT_TENANT_ID);
        String id = JsonPath.read(body(createDraft(job.cardId())), "$.id");
        RequestPostProcessor north = atLocations("SERVICE_ADVISOR", "north-desk", NORTH);

        mvc.perform(get("/api/v1/invoices/" + id).with(north)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invoices").param("q", job.registration()).with(north))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/v1/invoices").param("q", job.registration()).with(advisor))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
        mvc.perform(get("/api/v1/invoices").param("status", "UNPAID").param("q", job.registration()).with(advisor))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private record Job(String cardId, UUID customerId, String registration) {
    }

    /** A job card taken all the way to COMPLETED in {@code tenant}'s Main location. */
    private Job completedJob(UUID tenant) throws Exception {
        UUID customerId = UUID.randomUUID();
        String registration = "R" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String card = openCard(tenant, customerId, registration);
        RequestPostProcessor desk = staff(tenant, "SERVICE_ADVISOR", "sam", "*");
        RequestPostProcessor tech = staff(tenant, "MECHANIC", "mike", "*");
        RequestPostProcessor owner = customer(tenant, customerId);

        act(tech, card, "findings", "{\"description\":\"Front pads at 2 mm\",\"severity\":\"HIGH\"}");
        String item = JsonPath.read(body(act(desk, card, "recommendations",
                "{\"description\":\"Replace front pads\",\"estimatedPartsCost\":90.50,\"estimatedLabourHours\":1.25}")),
                "$.recommendations[0].id");
        act(desk, card, "submit-for-approval", null);
        act(owner, card, "recommendations/" + item + "/approve", null);
        act(tech, card, "start", null);
        act(tech, card, "work-logs", "{\"recommendationId\":\"%s\",\"description\":\"Fitted\"}".formatted(item));
        act(tech, card, "parts", """
                {"recommendationId":"%s","partNumber":"BP-220","name":"Front brake pad set","quantity":2,"unitPrice":45.25}
                """.formatted(item));
        act(tech, card, "labour", "{\"recommendationId\":\"%s\",\"hours\":1.25}".formatted(item));
        act(tech, card, "complete", null);
        return new Job(card, customerId, registration);
    }

    private String openCard(UUID tenant, UUID customerId) throws Exception {
        return openCard(tenant, customerId == null ? UUID.randomUUID() : customerId, "OPEN 1");
    }

    private String openCard(UUID tenant, UUID customerId, String registration) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"%s",
                                 "customerUserId":"%s","customerName":"A Silva","mileage":42000,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), registration, customerId, tenant)))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private ResultActions createDraft(String cardId) throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + cardId + "/invoice").with(advisor));
    }

    private ResultActions issue(String id, int version) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + id + "/issue").with(advisor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"));
    }

    private ResultActions pay(RequestPostProcessor who, String id, String amount, String key) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + id + "/payments").with(who).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"CARD\",\"amount\":" + amount + ",\"reference\":\"slip 42\"}"));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** Staff of another organization ({@code tenant}) at the given locations ("*" = all). */
    private static RequestPostProcessor staff(UUID tenant, String role, String username, String... locations) {
        return jwt()
                .jwt(token -> token.subject(UUID.randomUUID().toString())
                        .claim("username", username)
                        .claim("roles", List.of(role))
                        .claim(Tenants.CLAIM, tenant.toString())
                        .claim(Tenants.LOCATIONS_CLAIM, List.of(locations)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static RequestPostProcessor customer(UUID tenant, UUID userId) {
        return jwt()
                .jwt(token -> token.subject(userId.toString())
                        .claim("username", "customer")
                        .claim("roles", List.of("CUSTOMER"))
                        .claim(Tenants.CLAIM, tenant.toString())
                        .claim(Tenants.LOCATIONS_CLAIM, List.of("*")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
