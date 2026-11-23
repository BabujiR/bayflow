package com.bayflow.workshop.cannedjobs;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;
import com.jayway.jsonpath.JsonPath;

/** Canned jobs: managers keep the list, advisors recommend from it, mechanics add its usual work. */
class CannedJobsIT extends IntegrationTestBase {

    private static final String BRAKES = """
            {"name":"Front brakes – pads & rotors","category":"Brakes",
             "description":"Replace front brake pads and rotors","labourHours":1.5,
             "parts":[{"partNumber":"BP-220","name":"Front brake pad set","quantity":1,"unitPrice":65.00},
                      {"partNumber":"RT-118","name":"Front rotor","quantity":2,"unitPrice":48.50}]%s}""";

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    private UUID tenant;
    private UUID main;
    private RequestPostProcessor manager;
    private RequestPostProcessor advisor;
    private RequestPostProcessor mechanic;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        main = tenant; // an organization's first location has its id
        manager = as("MANAGER", UUID.randomUUID().toString(), "maria", tenant);
        advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam", tenant);
        mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mike", tenant);
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("100.00"), "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void managersKeepTheListAndEveryoneOnStaffReadsIt() throws Exception {
        create(advisor, BRAKES.formatted("")).andExpect(status().isForbidden());
        String id = JsonPath.read(body(create(manager, BRAKES.formatted("")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.parts", hasSize(2)))
                .andExpect(jsonPath("$.partsTotal", closeTo(162.00, 0.001)))
                .andExpect(jsonPath("$.locationId").value(org.hamcrest.Matchers.nullValue()))), "$.id");
        create(manager, """
                {"name":"Oil change","category":"Maintenance","description":"Synthetic oil and filter",
                 "labourHours":0.5,"parts":[]}""").andExpect(status().isCreated());
        create(manager, "{\"name\":\"\",\"description\":\"x\",\"labourHours\":-1,\"parts\":[]}")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/canned-jobs").with(advisor)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].category").value("Brakes"))
                .andExpect(jsonPath("$[1].name").value("Oil change"));
        mvc.perform(get("/api/v1/canned-jobs").param("q", "ROTOR").with(mechanic))
                .andExpect(jsonPath("$", hasSize(1)));

        // Edited with the version read; a stale one is refused.
        mvc.perform(put("/api/v1/canned-jobs/" + id).with(manager).contentType(MediaType.APPLICATION_JSON)
                        .content(BRAKES.formatted(",\"version\":0").replace("\"labourHours\":1.5", "\"labourHours\":1.75")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.labourHours", closeTo(1.75, 0.001)));
        mvc.perform(put("/api/v1/canned-jobs/" + id).with(manager).contentType(MediaType.APPLICATION_JSON)
                        .content(BRAKES.formatted(",\"version\":0")))
                .andExpect(status().isConflict());

        // Archived: gone from the picker, still on the settings page.
        mvc.perform(put("/api/v1/canned-jobs/" + id + "/active").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/canned-jobs").with(advisor)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/canned-jobs").param("includeInactive", "true").with(manager))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void aRecommendationFromACannedJobAndItsUsualWorkInOneGo() throws Exception {
        String job = JsonPath.read(body(create(manager, BRAKES.formatted("")).andExpect(status().isCreated())),
                "$.id");
        String card = openCard();
        act(mechanic, card, "findings", "{\"description\":\"Pads at 2 mm, rotors scored\",\"severity\":\"HIGH\"}")
                .andExpect(status().isOk());
        String item = JsonPath.read(body(act(advisor, card, "recommendations", """
                {"description":"Replace front brake pads and rotors","estimatedPartsCost":162.00,
                 "estimatedLabourHours":1.5,"cannedJobId":"%s"}""".formatted(job))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].cannedJobId").value(job))), "$.recommendations[0].id");
        // An unknown canned job is refused.
        act(advisor, card, "recommendations", """
                {"description":"x","estimatedPartsCost":1,"estimatedLabourHours":1,"cannedJobId":"%s"}"""
                .formatted(UUID.randomUUID())).andExpect(status().isBadRequest());

        act(advisor, card, "submit-for-approval", null).andExpect(status().isOk());
        act(advisor, card, "recommendations/" + item + "/approve", null).andExpect(status().isOk());
        act(mechanic, card, "start", null).andExpect(status().isOk());

        String work = """
                {"parts":[{"partNumber":"BP-220","name":"Front brake pad set","quantity":1,"unitPrice":65.00},
                          {"partNumber":"RT-118","name":"Front rotor","quantity":2,"unitPrice":48.50}],
                 "hours":1.5}""";
        act(advisor, card, "recommendations/" + item + "/work", work).andExpect(status().isForbidden());
        act(mechanic, card, "recommendations/" + item + "/work", "{\"parts\":[]}").andExpect(status().isBadRequest());
        act(mechanic, card, "recommendations/" + item + "/work", work).andExpect(status().isOk())
                .andExpect(jsonPath("$.parts", hasSize(2)))
                .andExpect(jsonPath("$.labour", hasSize(1)))
                .andExpect(jsonPath("$.labour[0].hourlyRate", closeTo(100.00, 0.001)));
    }

    @Test
    void aLocationsOwnJobIsOfferedOnlyThere() throws Exception {
        UUID north = UUID.randomUUID();
        RequestPostProcessor admin = as("ADMIN", UUID.randomUUID().toString(), "owner", tenant);
        String northJob = JsonPath.read(body(create(admin, BRAKES.formatted(",\"locationId\":\"" + north + "\""))
                .andExpect(status().isCreated())), "$.id");
        create(admin, "{\"name\":\"Wipers\",\"description\":\"Wiper blades\",\"labourHours\":0.25,\"parts\":[]}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/canned-jobs").param("locationId", main.toString()).with(advisor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Wipers"));
        mvc.perform(get("/api/v1/canned-jobs").param("locationId", north.toString()).with(advisor))
                .andExpect(jsonPath("$", hasSize(2)));

        String card = openCard();
        act(mechanic, card, "findings", "{\"description\":\"Worn pads\",\"severity\":\"HIGH\"}").andExpect(status().isOk());
        act(advisor, card, "recommendations", """
                {"description":"Brakes","estimatedPartsCost":162,"estimatedLabourHours":1.5,"cannedJobId":"%s"}"""
                .formatted(northJob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:unknown-canned-job"));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private ResultActions create(RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/v1/canned-jobs").with(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** An OPEN job card at the organization's first location. */
    private String openCard() throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"CJ 1",
                                 "customerUserId":"%s","customerName":"Ana Silva","mileage":42000,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tenant)))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
