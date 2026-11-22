package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * Reports, phase R1: the owner dashboard's figures and the report lists. Each test runs in a
 * workshop of its own, so other tests' job cards don't change the totals.
 * <p>
 * The shop: two finished jobs (one at Main by advisor sam, one at North by manager nora, both
 * worked by mike), one declined item, and one estimate still waiting for the customer.
 */
class ReportsIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    private UUID tenant;
    private UUID main;
    private UUID north;
    private RequestPostProcessor admin;
    private RequestPostProcessor sam;
    private RequestPostProcessor nora;
    private RequestPostProcessor mike;
    private Instant from;
    private Instant to;

    @BeforeEach
    void aWeekOfWork() throws Exception {
        tenant = UUID.randomUUID();
        main = tenant;
        north = UUID.randomUUID();
        admin = staff("ADMIN", "owner", "*");
        sam = staff("SERVICE_ADVISOR", "sam", main.toString());
        nora = staff("MANAGER", "nora", north.toString());
        mike = staff("MECHANIC", "mike", main.toString(), north.toString());
        from = Instant.now().minus(Duration.ofHours(1));
        to = Instant.now().plus(Duration.ofHours(1));

        // Main, by sam: pads approved (90.50 + 1.25 h × 95 = 209.25), wipers declined (30 + 0.25 h = 53.75).
        Card a = open(main);
        String pads = recommend(sam, a, "Replace front pads", "90.50", "1.25");
        String wipers = recommend(sam, a, "Replace wiper blades", "30", "0.25");
        act(sam, a, "submit-for-approval", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedBy").value("sam"));
        act(customer(a), a, "recommendations/" + pads + "/approve", null).andExpect(status().isOk());
        act(customer(a), a, "recommendations/" + wipers + "/decline", null).andExpect(status().isOk());
        work(a, pads, "BP-220", "Front brake pad set", 2, "45.25", "1.25");

        // North, by nora (a manager): brakes approved (100 + 2 h × 95 = 290.00).
        Card b = open(north);
        String brakes = recommend(nora, b, "Rear brake discs", "100", "2");
        act(nora, b, "submit-for-approval", null).andExpect(status().isOk());
        act(customer(b), b, "recommendations/" + brakes + "/approve", null).andExpect(status().isOk());
        work(b, brakes, "RD-10", "Rear disc pair", 1, "100", "2");

        // Main, by sam: an estimate still waiting (50 + 1 h × 95 = 145.00).
        Card c = open(main);
        recommend(sam, c, "Coolant flush", "50", "1");
        act(sam, c, "submit-for-approval", null).andExpect(status().isOk());
    }

    @Test
    void theSummaryAddsUpTheCompletedJobsAndTheEstimates() throws Exception {
        report("summary", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.carCount").value(2))
                .andExpect(jsonPath("$.current.sales", closeTo(499.25, 0.001)))
                .andExpect(jsonPath("$.current.partsSales", closeTo(190.50, 0.001)))
                .andExpect(jsonPath("$.current.labourSales", closeTo(308.75, 0.001)))
                .andExpect(jsonPath("$.current.averageRepairOrder", closeTo(249.63, 0.001)))
                .andExpect(jsonPath("$.current.labourHours", closeTo(3.25, 0.001)))
                .andExpect(jsonPath("$.current.effectiveLabourRate", closeTo(95.00, 0.001)))
                .andExpect(jsonPath("$.current.itemsApproved").value(2))
                .andExpect(jsonPath("$.current.itemsDeclined").value(1))
                .andExpect(jsonPath("$.current.approvedValue", closeTo(499.25, 0.001)))
                .andExpect(jsonPath("$.current.declinedValue", closeTo(53.75, 0.001)))
                .andExpect(jsonPath("$.current.closeRatio", closeTo(0.9028, 0.00001)))
                .andExpect(jsonPath("$.previous.carCount").value(0))
                .andExpect(jsonPath("$.previous.closeRatio").value(nullValue()))
                .andExpect(jsonPath("$.open.openCards").value(1))
                .andExpect(jsonPath("$.open.awaitingApproval").value(1))
                .andExpect(jsonPath("$.open.inWork").value(0))
                .andExpect(jsonPath("$.open.pendingValue", closeTo(145.00, 0.001)));
    }

    @Test
    void salesPerDayTechnicianAdvisorAndLocation() throws Exception {
        mvc.perform(withPeriod(get("/api/v1/reports/daily")).param("zone", "America/New_York").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("America/New_York"))
                .andExpect(jsonPath("$.days[*].carCount", everyItem(greaterThan(0))));

        report("technicians", admin)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mechanic").value("mike"))
                .andExpect(jsonPath("$[0].hours", closeTo(3.25, 0.001)))
                .andExpect(jsonPath("$[0].labourValue", closeTo(308.75, 0.001)))
                .andExpect(jsonPath("$[0].jobCards").value(2))
                .andExpect(jsonPath("$[0].hoursOnCompleted", closeTo(3.25, 0.001)));

        report("advisors", admin)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].advisor").value("sam"))
                .andExpect(jsonPath("$[0].estimatesSent").value(2))
                .andExpect(jsonPath("$[0].presentedValue", closeTo(408.00, 0.001)))
                .andExpect(jsonPath("$[0].approvedValue", closeTo(209.25, 0.001)))
                .andExpect(jsonPath("$[0].declinedValue", closeTo(53.75, 0.001)))
                .andExpect(jsonPath("$[0].completedCards").value(1))
                .andExpect(jsonPath("$[0].completedSales", closeTo(209.25, 0.001)))
                .andExpect(jsonPath("$[1].advisor").value("nora"))
                .andExpect(jsonPath("$[1].closeRatio", closeTo(1.0, 0.00001)));

        report("locations", admin)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].locationId").value(north.toString()))
                .andExpect(jsonPath("$[0].sales", closeTo(290.00, 0.001)))
                .andExpect(jsonPath("$[1].locationId").value(main.toString()))
                .andExpect(jsonPath("$[1].labourHours", closeTo(1.25, 0.001)));
    }

    @Test
    void declinedWorkAndPartsUsageList() throws Exception {
        report("declined", admin)
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].description").value("Replace wiper blades"))
                .andExpect(jsonPath("$.rows[0].value", closeTo(53.75, 0.001)))
                .andExpect(jsonPath("$.rows[0].customerName").value("Ana Silva"))
                .andExpect(jsonPath("$.rows[0].submittedBy").value("sam"));

        report("parts", admin)
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[?(@.partNumber == 'BP-220')].total").value(
                        contains(closeTo(90.50, 0.001))))
                .andExpect(jsonPath("$.rows[?(@.partNumber == 'BP-220')].recommendation").value(
                        contains("Replace front pads")));
    }

    @Test
    void managersSeeTheirLocationsAndTheSwitcherNarrows() throws Exception {
        report("summary", nora)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.carCount").value(1))
                .andExpect(jsonPath("$.current.sales", closeTo(290.00, 0.001)))
                .andExpect(jsonPath("$.open.openCards").value(0));

        mvc.perform(withPeriod(get("/api/v1/reports/summary")).with(admin)
                        .header("X-Bayflow-Location", main.toString()))
                .andExpect(jsonPath("$.current.carCount").value(1))
                .andExpect(jsonPath("$.current.sales", closeTo(209.25, 0.001)))
                .andExpect(jsonPath("$.open.openCards").value(1));
    }

    @Test
    void onlyOwnersAndManagersSeeReports() throws Exception {
        report("summary", sam).andExpect(status().isForbidden());
        report("technicians", mike).andExpect(status().isForbidden());
        report("summary", customer(new Card(null, UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @Test
    void thePeriodIsChecked() throws Exception {
        mvc.perform(get("/api/v1/reports/summary").param("from", to.toString()).param("to", from.toString())
                        .with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-period"));
        mvc.perform(get("/api/v1/reports/summary").param("from", from.minus(Duration.ofDays(400)).toString())
                        .param("to", to.toString()).with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withPeriod(get("/api/v1/reports/daily")).param("zone", "Mars/Olympus").with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-zone"));
        mvc.perform(get("/api/v1/reports/summary").with(admin)).andExpect(status().isBadRequest());
    }

    // ---- helpers ------------------------------------------------------------------------

    private record Card(String id, UUID customerId) {
    }

    /** Staff of this test's workshop at the given locations ("*" = all). */
    private RequestPostProcessor staff(String role, String username, String... locations) {
        List<SimpleGrantedAuthority> authorities = "MANAGER".equals(role)
                ? List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("ROLE_SERVICE_ADVISOR"))
                : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return jwt()
                .jwt(token -> token.subject(UUID.randomUUID().toString())
                        .claim("username", username)
                        .claim("roles", List.of(role))
                        .claim(Tenants.CLAIM, tenant.toString())
                        .claim(Tenants.LOCATIONS_CLAIM, Arrays.asList(locations)))
                .authorities(authorities.toArray(new SimpleGrantedAuthority[0]));
    }

    private RequestPostProcessor customer(Card card) {
        return jwt()
                .jwt(token -> token.subject(card.customerId().toString())
                        .claim("username", "customer")
                        .claim("roles", List.of("CUSTOMER"))
                        .claim(Tenants.CLAIM, tenant.toString())
                        .claim(Tenants.LOCATIONS_CLAIM, List.of("*")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private Card open(UUID location) throws Exception {
        UUID customerId = UUID.randomUUID();
        String body = mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352",
                                 "registration":"REP 1","customerUserId":"%s","customerName":"Ana Silva",
                                 "mileage":1000,"tenantId":"%s","locationId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), customerId, tenant, location)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Card card = new Card(JsonPath.read(body, "$.id"), customerId);
        // A finding makes the card INSPECTED: only then can the estimate be sent.
        act(mike, card, "findings", "{\"description\":\"Checked over\",\"severity\":\"LOW\"}")
                .andExpect(status().isOk());
        return card;
    }

    /** Adds a recommendation and returns its id. */
    private String recommend(RequestPostProcessor advisor, Card card, String description, String parts,
            String hours) throws Exception {
        String body = act(advisor, card, "recommendations", """
                {"description":"%s","estimatedPartsCost":%s,"estimatedLabourHours":%s}
                """.formatted(description, parts, hours))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.recommendations[?(@.description == '" + description + "')].id");
        return ids.getFirst();
    }

    /** mike does the approved item and the advisor completes the card. */
    private void work(Card card, String item, String partNumber, String partName, int quantity, String unitPrice,
            String hours) throws Exception {
        act(mike, card, "start", null).andExpect(status().isOk());
        act(mike, card, "work-logs", """
                {"recommendationId":"%s","description":"Done"}
                """.formatted(item)).andExpect(status().isOk());
        act(mike, card, "parts", """
                {"recommendationId":"%s","partNumber":"%s","name":"%s","quantity":%d,"unitPrice":%s}
                """.formatted(item, partNumber, partName, quantity, unitPrice)).andExpect(status().isOk());
        act(mike, card, "labour", """
                {"recommendationId":"%s","hours":%s}
                """.formatted(item, hours)).andExpect(status().isOk());
        act(mike, card, "complete", null).andExpect(status().isOk());
    }

    private ResultActions act(RequestPostProcessor who, Card card, String action, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card.id() + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private MockHttpServletRequestBuilder withPeriod(MockHttpServletRequestBuilder request) {
        return request.param("from", from.toString()).param("to", to.toString());
    }

    private ResultActions report(String name, RequestPostProcessor who) throws Exception {
        return mvc.perform(withPeriod(get("/api/v1/reports/" + name)).with(who));
    }
}
