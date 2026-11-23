package com.bayflow.workshop.quickbooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.bayflow.workshop.integration.WorkshopSettingsClient.LocationBilling;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;
import com.bayflow.workshop.quickbooks.QuickBooksApi.QuickBooksException;
import com.bayflow.workshop.quickbooks.QuickBooksApi.Tokens;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * QuickBooks Online, with Intuit mocked: connecting through the one central callback, the
 * product/service mapping, and sending invoices, payments and voids. Each test is its own
 * organization. The job is the one of InvoiceIT: 227.56 with 8.87 tax (none on labour).
 */
@TestPropertySource(properties = {
        "bayflow.workshop.site-url=https://example.test",
        "bayflow.workshop.quickbooks.client-id=test-client",
        "bayflow.workshop.quickbooks.client-secret=test-secret",
        // 32 zero bytes, tests only.
        "bayflow.workshop.quickbooks.token-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
class QuickBooksIT extends IntegrationTestBase {

    private static final String REALM = "9130";

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    @Autowired
    private ObjectMapper json;

    private UUID tenant;
    private RequestPostProcessor admin;
    private RequestPostProcessor advisor;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = as("ADMIN", UUID.randomUUID().toString(), "owner", tenant);
        advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam", tenant);
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("95.00"), "USD", new LocationBilling(
                new BigDecimal("8.875"), BigDecimal.ZERO, new BigDecimal("8.875"), new BigDecimal("5"),
                new BigDecimal("35.00"), new BigDecimal("3.50"), 15, null)))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(new BillingContact(UUID.randomUUID(), "Ana Silva", "ana@example.com", "+1 555 0100",
                "Toyota", "Corolla", 2019)).when(billingContacts).get(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doAnswer(call -> "https://intuit.test/connect?state=" + call.getArgument(0))
                .when(quickBooksApi).authorizeUrl(any());
    }

    @Test
    void anAdminConnectsTheCompanyThroughTheCentralCallback() throws Exception {
        mvc.perform(get("/api/v1/quickbooks").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.connected").value(false));
        // Only an admin connects, and only back to this site.
        connect(advisor, "https://main.example.test/settings").andExpect(status().isForbidden());
        connect(admin, "https://evil.example.com/settings").andExpect(status().isBadRequest());
        connect(admin, "https://main.example.test.evil.com/settings").andExpect(status().isBadRequest());

        String state = startConnecting();
        assertThat(QuickBooksService.tenantOf(state)).contains(tenant);
        callback(state, "code-1").andExpect(status().isFound())
                .andExpect(header().string("Location", "https://main.example.test/settings?quickbooks=connected"));
        // A state works once.
        callback(state, "code-1").andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.test/?quickbooks=failed"));
        // Cancelled at Intuit.
        mvc.perform(get("/api/v1/quickbooks/callback").param("state", startConnecting()).param("error", "access_denied"))
                .andExpect(header().string("Location", "https://main.example.test/settings?quickbooks=cancelled"));

        mvc.perform(get("/api/v1/quickbooks").with(advisor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.companyName").value("Sandbox Co"))
                .andExpect(jsonPath("$.environment").value("sandbox"))
                .andExpect(jsonPath("$.connectedBy").value("owner"));
        // The tokens are stored encrypted.
        String stored = ownerJdbc().queryForObject(
                "select access_token || ' ' || refresh_token from bayflow.ws_qbo_connections where tenant_id = ?",
                String.class, tenant);
        assertThat(stored).startsWith("v1:").doesNotContain("access-1").doesNotContain("refresh-1");

        Mockito.doReturn(node("""
                {"Item":[{"Id":"1","Name":"Parts","Type":"NonInventory"},{"Id":"2","Name":"Labor","Type":"Service"},
                 {"Id":"3","Name":"Shop fees","Type":"Service"},{"Id":"4","Name":"Brakes","Type":"Category"}]}"""))
                .when(quickBooksApi).query(eq(REALM), eq("access-1"), startsWith("select * from Item"));
        mvc.perform(get("/api/v1/quickbooks/items").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Labor"));
        map().andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/quickbooks/connection").with(admin))
                .andExpect(status().isNoContent());
        Mockito.verify(quickBooksApi).revoke("refresh-1");
        mvc.perform(get("/api/v1/quickbooks").with(admin)).andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void anInvoiceGoesWithItsTaxAndPaymentsOnceThenOnlyWhatIsNew() throws Exception {
        connected();
        String id = issuedInvoice();
        pay(advisor, id, "100.00", "q-1").andExpect(status().isOk());
        Mockito.doReturn(node("{}")).when(quickBooksApi).query(eq(REALM), eq("access-1"),
                eq("select * from Customer where DisplayName = 'Ana Silva'"));
        Mockito.doReturn(node("{\"Customer\":{\"Id\":\"58\"}}")).when(quickBooksApi)
                .post(eq(REALM), eq("access-1"), eq("customer"), isNull(), any());
        Mockito.doReturn(node("{\"Invoice\":{\"Id\":\"130\",\"SyncToken\":\"0\"}}")).when(quickBooksApi)
                .post(eq(REALM), eq("access-1"), eq("invoice"), isNull(), any());
        Mockito.doReturn(node("{\"Payment\":{\"Id\":\"200\"}}")).when(quickBooksApi)
                .post(eq(REALM), eq("access-1"), eq("payment"), isNull(), any());

        send(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.quickbooks.status").value("SENT"))
                .andExpect(jsonPath("$.quickbooks.invoiceId").value("130"))
                .andExpect(jsonPath("$.quickbooks.paymentsToSend").value(0));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(quickBooksApi).post(eq(REALM), eq("access-1"), eq("invoice"), isNull(), sent.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) sent.getValue();
        assertThat(invoice.get("DocNumber")).isEqualTo("INV-1001");
        assertThat(invoice.get("CustomerRef")).isEqualTo(Map.of("value", "58"));
        assertThat(((Map<?, ?>) invoice.get("TxnTaxDetail")).get("TotalTax")).isEqualTo(new BigDecimal("8.87"));
        List<?> lines = (List<?>) invoice.get("Line");
        assertThat(lines).hasSize(4);
        assertThat(detail(lines, 0)).containsEntry("ItemRef", Map.of("value", "1"))
                .containsEntry("TaxCodeRef", Map.of("value", "TAX"));
        assertThat(detail(lines, 1)).containsEntry("ItemRef", Map.of("value", "2"))
                .containsEntry("TaxCodeRef", Map.of("value", "NON"));
        assertThat(detail(lines, 2)).containsEntry("ItemRef", Map.of("value", "3"));

        // Again: nothing new. A refund is for the bookkeeper; a new payment goes on its own.
        send(id).andExpect(jsonPath("$.quickbooks.status").value("SENT"));
        pay(as("ADMIN", UUID.randomUUID().toString(), "owner", tenant), id, "-10.00", "q-2").andExpect(status().isOk());
        pay(advisor, id, "20.00", "q-3").andExpect(status().isOk())
                .andExpect(jsonPath("$.quickbooks.status").value("OUT_OF_DATE"))
                .andExpect(jsonPath("$.quickbooks.paymentsToSend").value(1))
                .andExpect(jsonPath("$.quickbooks.refundsByHand").value(1));
        send(id).andExpect(jsonPath("$.quickbooks.status").value("SENT"));
        Mockito.verify(quickBooksApi, Mockito.times(1)).post(eq(REALM), any(), eq("invoice"), isNull(), any());
        Mockito.verify(quickBooksApi, Mockito.times(2)).post(eq(REALM), any(), eq("payment"), isNull(), any());

        // QuickBooks says no: the error stays on the invoice, with what was sent before it.
        pay(advisor, id, "5.00", "q-4").andExpect(status().isOk());
        Mockito.doThrow(new QuickBooksException(400, "Business Validation Error: bad date"))
                .when(quickBooksApi).post(eq(REALM), eq("access-1"), eq("payment"), isNull(), any());
        send(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.quickbooks.status").value("FAILED"))
                .andExpect(jsonPath("$.quickbooks.error").value("Business Validation Error: bad date"))
                .andExpect(jsonPath("$.quickbooks.invoiceId").value("130"));
    }

    @Test
    void aVoidedInvoiceIsVoidedThereAndExpiredTokensAreRefreshed() throws Exception {
        connected();
        String id = issuedInvoice();
        Mockito.doReturn(node("{\"Customer\":[{\"Id\":\"58\"}]}")).when(quickBooksApi)
                .query(eq(REALM), any(), startsWith("select * from Customer"));
        Mockito.doReturn(node("{\"Invoice\":{\"Id\":\"131\",\"SyncToken\":\"0\"}}")).when(quickBooksApi)
                .post(eq(REALM), any(), eq("invoice"), isNull(), any());
        send(id).andExpect(jsonPath("$.quickbooks.status").value("SENT"));
        Mockito.verify(quickBooksApi, Mockito.never()).post(eq(REALM), any(), eq("customer"), any(), any());

        mvc.perform(post("/api/v1/invoices/" + id + "/void").with(as("MANAGER", UUID.randomUUID().toString(), "maria",
                        tenant)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Duplicate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quickbooks.status").value("OUT_OF_DATE"));

        // The access token was refused: refreshed once (the new refresh token kept), then voided.
        Mockito.doThrow(new QuickBooksException(401, "AuthenticationFailed")).when(quickBooksApi)
                .get(eq(REALM), eq("access-1"), eq("invoice/131"));
        Mockito.doReturn(new Tokens("access-2", Instant.now().plusSeconds(3600), "refresh-2",
                Instant.now().plus(Duration.ofDays(100)))).when(quickBooksApi).refresh(eq("refresh-1"), any());
        Mockito.doReturn(node("{\"Invoice\":{\"Id\":\"131\",\"SyncToken\":\"3\"}}")).when(quickBooksApi)
                .get(eq(REALM), eq("access-2"), eq("invoice/131"));
        Mockito.doReturn(node("{}")).when(quickBooksApi).post(eq(REALM), eq("access-2"), eq("invoice"), eq("void"), any());
        send(id).andExpect(status().isOk()).andExpect(jsonPath("$.quickbooks.status").value("SENT"));
        Mockito.verify(quickBooksApi).post(REALM, "access-2", "invoice", "void", Map.of("Id", "131", "SyncToken", "3"));

        // A draft or a never-sent void can't be sent; nor anything without a connection.
        mvc.perform(post("/api/v1/invoices/" + draft() + "/quickbooks").with(advisor)).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/quickbooks/connection").with(admin)).andExpect(status().isNoContent());
        send(issuedInvoice()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:quickbooks-not-connected"));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String startConnecting() throws Exception {
        String url = JsonPath.read(body(connect(admin, "https://main.example.test/settings")
                .andExpect(status().isOk())), "$.authorizeUrl");
        return url.substring(url.indexOf("state=") + "state=".length());
    }

    private ResultActions callback(String state, String code) throws Exception {
        Mockito.doReturn(new Tokens("access-1", Instant.now().plusSeconds(3600), "refresh-1",
                Instant.now().plus(Duration.ofDays(100)))).when(quickBooksApi).exchangeCode(eq(code), any());
        Mockito.doReturn(node("{\"CompanyInfo\":{\"CompanyName\":\"Sandbox Co\",\"Country\":\"US\"}}"))
                .when(quickBooksApi).get(REALM, "access-1", "companyinfo/" + REALM);
        return mvc.perform(get("/api/v1/quickbooks/callback").param("code", code).param("state", state)
                .param("realmId", REALM));
    }

    /** Connected to the sandbox company, with parts → 1, labour → 2, fees → 3. */
    private void connected() throws Exception {
        callback(startConnecting(), "code-1").andExpect(status().isFound());
        map().andExpect(status().isOk());
    }

    private ResultActions connect(RequestPostProcessor who, String returnUrl) throws Exception {
        return mvc.perform(post("/api/v1/quickbooks/connect").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"returnUrl\":\"" + returnUrl + "\"}"));
    }

    private ResultActions map() throws Exception {
        return mvc.perform(put("/api/v1/quickbooks/mapping").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"partsItemId":"1","partsItemName":"Parts","labourItemId":"2","labourItemName":"Labor",
                         "feesItemId":"3","feesItemName":"Shop fees"}"""));
    }

    private ResultActions send(String id) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + id + "/quickbooks").with(advisor));
    }

    private ResultActions pay(RequestPostProcessor who, String id, String amount, String key) throws Exception {
        return mvc.perform(post("/api/v1/invoices/" + id + "/payments").with(who).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CARD\",\"amount\":" + amount + "}"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detail(List<?> lines, int index) {
        return (Map<String, Object>) ((Map<?, ?>) lines.get(index)).get("SalesItemLineDetail");
    }

    private JsonNode node(String text) {
        return json.readTree(text);
    }

    private String issuedInvoice() throws Exception {
        String id = draft();
        mvc.perform(post("/api/v1/invoices/" + id + "/issue").with(advisor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isOk());
        return id;
    }

    /** A draft invoice for a job taken to COMPLETED (2 brake pads at 45.25, 1.25 h labour). */
    private String draft() throws Exception {
        UUID customerId = UUID.randomUUID();
        String card = JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"QB 1",
                                 "customerUserId":"%s","customerName":"A Silva","mileage":42000,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), customerId, tenant)))
                .andExpect(status().isCreated())), "$.id");
        RequestPostProcessor tech = as("MECHANIC", UUID.randomUUID().toString(), "mike", tenant);
        RequestPostProcessor owner = as("CUSTOMER", customerId.toString(), "ana", tenant);
        act(tech, card, "findings", "{\"description\":\"Front pads at 2 mm\",\"severity\":\"HIGH\"}");
        String item = JsonPath.read(body(act(advisor, card, "recommendations",
                "{\"description\":\"Replace front pads\",\"estimatedPartsCost\":90.50,\"estimatedLabourHours\":1.25}")),
                "$.recommendations[0].id");
        act(advisor, card, "submit-for-approval", null);
        act(owner, card, "recommendations/" + item + "/approve", null);
        act(tech, card, "start", null);
        act(tech, card, "work-logs", "{\"recommendationId\":\"%s\",\"description\":\"Fitted\"}".formatted(item));
        act(tech, card, "parts", """
                {"recommendationId":"%s","partNumber":"BP-220","name":"Front brake pad set","quantity":2,"unitPrice":45.25}
                """.formatted(item));
        act(tech, card, "labour", "{\"recommendationId\":\"%s\",\"hours\":1.25}".formatted(item));
        act(tech, card, "complete", null);
        return JsonPath.read(body(mvc.perform(post("/api/v1/job-cards/" + card + "/invoice").with(advisor))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
