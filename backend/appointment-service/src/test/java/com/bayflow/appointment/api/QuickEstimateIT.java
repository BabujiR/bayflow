package com.bayflow.appointment.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.integration.SystemTokenProvider;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.bayflow.security.Tenants;

/**
 * Quick estimates (a visit without a time, whose job card opens at once) and VIN decode. WireMock
 * stands in for workshop-service, auth-service's token endpoint and NHTSA vPIC.
 */
class QuickEstimateIT extends IntegrationTestBase {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("bayflow.integration.auth-url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.workshop-url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.vin-decoder.url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.vin-decoder.timeout", () -> "500ms");
        registry.add("bayflow.integration.vin-decoder.max-per-hour", () -> "3");
        registry.add("bayflow.integration.vin-decoder.public-max-per-hour", () -> "2");
    }

    private static final String JOB_CARDS = "/api/v1/job-cards";
    private static final String VALID_VIN = "1HGCM82633A004352";
    private static final Instant TOMORROW_10AM = Instant.parse("2026-11-17T15:00:00Z");

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());

    @Autowired
    private SystemTokenProvider tokens;

    @MockitoBean
    private WorkshopDirectory directory;

    private final UUID shopId = UUID.randomUUID();
    private final String slug = "vin-" + shopId.toString().substring(0, 8);

    @BeforeEach
    void resetRemoteServices() {
        ownerJdbc().update("delete from bayflow.apt_outbox");
        WIREMOCK.resetAll();
        tokens.invalidate();
        Mockito.when(directory.bySlug(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        Mockito.when(directory.bySlug(slug)).thenReturn(Optional.of(new Workshop(shopId, slug, "Lotus Auto LLC",
                "Lotus Auto", true)));
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/token")).willReturn(json(200,
                "{\"accessToken\":\"system-token\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}")));
    }

    // ---- quick estimates ---------------------------------------------------------------------

    @Test
    void anEstimateOpensItsJobCardAtOnceAndTheSameCardCarriesOnAtCheckIn() throws Exception {
        UUID jobCardId = UUID.randomUUID();
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(JOB_CARDS)).willReturn(json(201,
                "{\"id\":\"" + jobCardId + "\",\"status\":\"OPEN\",\"mileage\":1200,\"findings\":[]}")));
        String vehicleId = vehicle(as("CUSTOMER", UUID.randomUUID()));

        String estimate = createEstimate(vehicleId, "Brakes squeal; quote front pads")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ESTIMATE"))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(estimate, "$.id");
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id)))
                .withRequestBody(matchingJsonPath("$.estimate", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.request", equalTo("Brakes squeal; quote front pads")))
                .withRequestBody(matchingJsonPath("$.mileage", equalTo("1200"))));

        // Not on the day's list or board, but listed on its own; it doesn't hold a slot either.
        mvc.perform(get("/api/v1/appointments").param("vehicleId", vehicleId).with(advisor))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/v1/appointments").param("vehicleId", vehicleId).param("status", "ESTIMATE")
                        .with(advisor))
                .andExpect(jsonPath("$.content[0].id").value(id));

        // The customer goes ahead: booked, then the usual visit.
        schedule(id, TOMORROW_10AM).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.scheduledAt").value(TOMORROW_10AM.toString()))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));
        schedule(id, TOMORROW_10AM.plus(Duration.ofDays(1))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/arrive").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":1250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));

        // Check-in asks workshop-service again for the same appointment: it marks the card arrived.
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id))));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.mileage", equalTo("1250"))));
    }

    @Test
    void theShopClosesAnEstimateTheCustomerCannot() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(JOB_CARDS)).willReturn(aResponse().withStatus(503)));
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());
        String vehicleId = vehicle(customer);
        // workshop-service down: the estimate is still made, and its card follows on retry.
        String id = JsonPath.read(createEstimate(vehicleId, "Check engine light")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobCardId").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/appointments/" + id + "/cancel").with(customer))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/appointments/" + id + "/cancel").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        schedule(id, TOMORROW_10AM).andExpect(status().isConflict());
    }

    @Test
    void aLocationManagerWorksEstimatesLikeAnAdvisor() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(JOB_CARDS)).willReturn(aResponse().withStatus(503)));
        // The organization's only location has the tenant's id.
        RequestPostProcessor manager = atLocations("MANAGER", UUID.randomUUID(), Tenants.DEFAULT_TENANT_ID);
        String vehicleId = vehicle(as("CUSTOMER", UUID.randomUUID()));

        String id = JsonPath.read(mvc.perform(post("/api/v1/appointments/estimates").with(manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + vehicleId + "\",\"request\":\"Quote a service\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/appointments/" + id + "/schedule").with(manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledAt\":\"" + TOMORROW_10AM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void onlyTheDeskStartsEstimates() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());
        String vehicleId = vehicle(customer);
        mvc.perform(post("/api/v1/appointments/estimates").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + vehicleId + "\",\"request\":\"Quote\"}"))
                .andExpect(status().isForbidden());
        createEstimate(vehicleId, " ").andExpect(status().isBadRequest());
    }

    // ---- VIN decode --------------------------------------------------------------------------

    @Test
    void aVinIsDecodedAndCached() throws Exception {
        WIREMOCK.stubFor(vpic(VALID_VIN).willReturn(json(200, """
                {"Count":1,"Results":[{"ModelYear":"2003","Make":"HONDA","Model":"Accord","Trim":"EX",
                 "DisplacementL":"2.998832712","EngineCylinders":"6","FuelTypePrimary":"Gasoline",
                 "ElectrificationLevel":"","BodyClass":"Coupe"}]}
                """)));
        RequestPostProcessor desk = as("SERVICE_ADVISOR", UUID.randomUUID());

        decode(desk, VALID_VIN.toLowerCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$.vin").value(VALID_VIN))
                .andExpect(jsonPath("$.year").value(2003))
                .andExpect(jsonPath("$.make").value("Honda"))
                .andExpect(jsonPath("$.model").value("Accord"))
                .andExpect(jsonPath("$.trim").value("EX"))
                .andExpect(jsonPath("$.engine").value("3.0L 6-cyl Gasoline"))
                .andExpect(jsonPath("$.bodyClass").value("Coupe"))
                .andExpect(jsonPath("$.warnings.length()").value(0));
        decode(desk, VALID_VIN).andExpect(status().isOk());
        WIREMOCK.verify(1, WireMock.getRequestedFor(urlPathEqualTo("/vehicles/DecodeVinValues/" + VALID_VIN)));
    }

    @Test
    void aVinThatDoesntAddUpIsFlagged() throws Exception {
        String typo = "1HGCM82643A004352";
        WIREMOCK.stubFor(vpic(typo).willReturn(json(200,
                "{\"Results\":[{\"ModelYear\":\"2003\",\"Make\":\"HONDA\",\"Model\":\"Accord\"}]}")));
        decode(as("SERVICE_ADVISOR", UUID.randomUUID()), typo).andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(1));
    }

    @Test
    void unknownSlowOrMissingLookupsSayWhy() throws Exception {
        RequestPostProcessor desk = as("SERVICE_ADVISOR", UUID.randomUUID());
        decode(desk, "NOTAVIN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-vin"));

        String unknown = "11111111111111111";
        WIREMOCK.stubFor(vpic(unknown).willReturn(json(200, "{\"Results\":[{\"Make\":\"\",\"Model\":\"\"}]}")));
        decode(desk, unknown).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vin-not-decoded"));

        String slow = "2HGCM82633A004352";
        WIREMOCK.stubFor(vpic(slow).willReturn(json(200, "{\"Results\":[]}").withFixedDelay(2_000)));
        decode(as("SERVICE_ADVISOR", UUID.randomUUID()), slow).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vin-decoder-unavailable"));
    }

    @Test
    void lookupsAreLimitedPerUserAndStaffOnly() throws Exception {
        WIREMOCK.stubFor(vpic(VALID_VIN).willReturn(json(200,
                "{\"Results\":[{\"ModelYear\":\"2003\",\"Make\":\"HONDA\",\"Model\":\"Accord\"}]}")));
        RequestPostProcessor desk = as("SERVICE_ADVISOR", UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            decode(desk, VALID_VIN).andExpect(status().isOk());
        }
        decode(desk, VALID_VIN).andExpect(status().isTooManyRequests());
        decode(as("SERVICE_ADVISOR", UUID.randomUUID()), VALID_VIN).andExpect(status().isOk());
        decode(as("CUSTOMER", UUID.randomUUID()), VALID_VIN).andExpect(status().isForbidden());
    }

    @Test
    void theBookingPageLooksUpAVinWithinItsOwnLimit() throws Exception {
        WIREMOCK.stubFor(vpic(VALID_VIN).willReturn(json(200, """
                {"Results":[{"ModelYear":"2003","Make":"HONDA","Model":"Accord","Trim":"EX","BodyClass":"Coupe"}]}
                """)));

        publicVin(VALID_VIN, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2003))
                .andExpect(jsonPath("$.make").value("Honda"))
                .andExpect(jsonPath("$.model").value("Accord"))
                .andExpect(jsonPath("$.trim").doesNotExist());
        // Like every public change: only the web app (a header a cross-site page can't send).
        publicVin(VALID_VIN, false).andExpect(status().isForbidden());
        publicVin(VALID_VIN, true).andExpect(status().isOk());
        publicVin(VALID_VIN, true).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/booking/vin").param("vin", VALID_VIN)
                        .header("X-Forwarded-Host", "nobody.localhost:3000").header("X-Bayflow-Client", "web"))
                .andExpect(status().isNotFound());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static com.github.tomakehurst.wiremock.client.MappingBuilder vpic(String vin) {
        return WireMock.get(urlPathEqualTo("/vehicles/DecodeVinValues/" + vin));
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    private ResultActions decode(RequestPostProcessor user, String vin) throws Exception {
        return mvc.perform(get("/api/v1/vehicles/decode").param("vin", vin).with(user));
    }

    private ResultActions publicVin(String vin, boolean fromTheApp) throws Exception {
        var request = get("/api/v1/booking/vin").param("vin", vin).header("X-Forwarded-Host", slug + ".localhost:3000");
        return mvc.perform(fromTheApp ? request.header("X-Bayflow-Client", "web") : request);
    }

    private ResultActions createEstimate(String vehicleId, String request) throws Exception {
        return mvc.perform(post("/api/v1/appointments/estimates").with(advisor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":\"%s\",\"request\":\"%s\"}".formatted(vehicleId, request)));
    }

    private ResultActions schedule(String id, Instant at) throws Exception {
        return mvc.perform(post("/api/v1/appointments/" + id + "/schedule").with(advisor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledAt\":\"" + at + "\"}"));
    }

    /** A customer's car with its VIN and plate (an estimate's job card needs both). */
    private String vehicle(RequestPostProcessor customer) throws Exception {
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pat Lee\",\"email\":\"pat@example.com\"}"))
                .andExpect(status().isCreated());
        String vin = ("JT" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .replaceAll("[IOQ]", "1").substring(0, 17);
        String vehicle = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"EST 1","make":"Honda","model":"Civic","year":2019,
                                 "mileage":1200}
                                """.formatted(vin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(vehicle, "$.id");
    }
}
