package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * Organizations and locations, phase L2: staff work only with the job cards (and service history)
 * of their locations; everything else is 404, and the switcher header can't widen that (403).
 */
class LocationIsolationIT extends IntegrationTestBase {

    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;
    private static final UUID NORTH = UUID.fromString("0d0d0d0d-2222-4000-8000-0000000000d2");

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor mainAdvisor = atLocations("SERVICE_ADVISOR", "advisor-main", MAIN);
    private final RequestPostProcessor mainMechanic = atLocations("MECHANIC", "mechanic-main", MAIN);
    private final RequestPostProcessor northManager = atLocations("MANAGER", "manager-north", NORTH);
    private final RequestPostProcessor regional = atLocations("SERVICE_ADVISOR", "advisor-both", MAIN, NORTH);

    private String vehicleId;
    private String atMain;
    private String atNorth;

    @BeforeEach
    void aCardAtEachLocation() throws Exception {
        vehicleId = UUID.randomUUID().toString();
        atMain = open(MAIN);
        atNorth = open(NORTH);
    }

    @Test
    void listsOnlyShowTheStaffMembersLocations() throws Exception {
        mvc.perform(get("/api/v1/job-cards").param("size", "100").with(mainAdvisor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(atMain)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(atNorth))));
        mvc.perform(get("/api/v1/job-cards").param("size", "100").with(northManager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(atNorth)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(atMain))));
        mvc.perform(get("/api/v1/job-cards").param("size", "100").with(regional))
                .andExpect(jsonPath("$.content[*].id", hasItem(atMain)))
                .andExpect(jsonPath("$.content[*].id", hasItem(atNorth)));
    }

    @Test
    void anotherLocationsJobCardIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/job-cards/" + atNorth).with(mainAdvisor)).andExpect(status().isNotFound());
        act(mainMechanic, atNorth, "findings", "{\"description\":\"Pads\",\"severity\":\"HIGH\"}")
                .andExpect(status().isNotFound());
        act(mainAdvisor, atNorth, "recommendations",
                "{\"description\":\"Pads\",\"estimatedPartsCost\":10,\"estimatedLabourHours\":1}")
                .andExpect(status().isNotFound());
        act(mainAdvisor, atNorth, "submit-for-approval", null).andExpect(status().isNotFound());
        act(mainMechanic, atNorth, "start", null).andExpect(status().isNotFound());
        act(mainAdvisor, atNorth, "complete", null).andExpect(status().isNotFound());

        // A manager is an advisor at their own location.
        act(northManager, atNorth, "findings", "{\"description\":\"Pads\",\"severity\":\"HIGH\"}")
                .andExpect(status().isOk());
        act(northManager, atNorth, "recommendations",
                "{\"description\":\"Pads\",\"estimatedPartsCost\":10,\"estimatedLabourHours\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].description").value("Pads"));
        mvc.perform(get("/api/v1/job-cards/" + atNorth).with(northManager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].description").value("Pads"));
    }

    @Test
    void staffOpenCardsOnlyAtTheirLocations() throws Exception {
        openAs(mainAdvisor, NORTH).andExpect(status().isNotFound());
        openAs(mainAdvisor, MAIN).andExpect(status().isCreated());
    }

    @Test
    void theSwitcherNarrowsButNeverWidens() throws Exception {
        mvc.perform(get("/api/v1/job-cards").param("size", "100").with(regional)
                        .header("X-Bayflow-Location", NORTH.toString()))
                .andExpect(jsonPath("$.content[*].id", hasItem(atNorth)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(atMain))));
        mvc.perform(get("/api/v1/job-cards").with(mainAdvisor).header("X-Bayflow-Location", NORTH.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:location-not-allowed"));
    }

    // ---- helpers ------------------------------------------------------------------------

    private String open(UUID location) throws Exception {
        String body = mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                        .content(openJson(location, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(location.toString()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions openAs(RequestPostProcessor who, UUID location) throws Exception {
        return mvc.perform(post("/api/v1/job-cards").with(who).contentType(MediaType.APPLICATION_JSON)
                .content(openJson(location, false)));
    }

    private String openJson(UUID location, boolean withTenant) {
        String tenant = withTenant ? ",\"tenantId\":\"" + Tenants.DEFAULT_TENANT_ID + "\"" : "";
        return """
                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"LOC 1",
                 "customerUserId":"%s","customerName":"Ana Silva","mileage":1000,"locationId":"%s"%s}
                """.formatted(UUID.randomUUID(), vehicleId, UUID.randomUUID(), location, tenant);
    }

    private ResultActions act(RequestPostProcessor who, String cardId, String action, String json) throws Exception {
        var request = post("/api/v1/job-cards/" + cardId + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }
}
