package com.bayflow.workshop.reminders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

/**
 * Maintenance intervals: the starter list, what a finished job records, and when vehicles show
 * as due. Each test is its own organization (so its own starter list).
 */
class RemindersIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    private UUID tenant;
    private RequestPostProcessor admin;
    private RequestPostProcessor advisor;
    private RequestPostProcessor mechanic;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        admin = as("ADMIN", UUID.randomUUID().toString(), "owner", tenant);
        advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam", tenant);
        mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mike", tenant);
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("100.00"), "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void aWorkshopStartsWithCommonIntervalsThatAdminsChange() throws Exception {
        mvc.perform(get("/api/v1/service-intervals").with(advisor)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("Oil change"))
                .andExpect(jsonPath("$[0].miles").value(5000))
                .andExpect(jsonPath("$[0].months").value(6));
        mvc.perform(get("/api/v1/service-reminders/settings").with(advisor))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.remindDaysBefore").value(14))
                .andExpect(jsonPath("$.milesPerDay").value(30));

        interval(advisor, "{\"name\":\"Wipers\",\"months\":12}").andExpect(status().isForbidden());
        interval(admin, "{\"name\":\"Wipers\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:interval-empty"));
        interval(admin, "{\"name\":\"Wipers\",\"months\":12,\"keywords\":[\" Wiper \",\"wiper\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keywords", hasSize(1)))
                .andExpect(jsonPath("$.keywords[0]").value("wiper"));
        mvc.perform(get("/api/v1/service-intervals").with(admin)).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[5].name").value("Wipers"));

        mvc.perform(put("/api/v1/service-reminders/settings").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"remindDaysBefore\":7,\"milesPerDay\":40,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.milesPerDay").value(40));
    }

    @Test
    void aFinishedOilChangeIsDueAgainByTheMilesOrMonthsWhicheverComesFirst() throws Exception {
        UUID vehicle = UUID.randomUUID();
        complete(vehicle, "Synthetic oil change and filter", 42000, null);

        // 5,000 miles at the assumed 30 a day: in 167 days, before 6 months.
        mvc.perform(get("/api/v1/service-due").param("days", "30").with(advisor))
                .andExpect(jsonPath("$", hasSize(0)));
        String body = body(mvc.perform(get("/api/v1/service-due").param("days", "200").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vehicleId").value(vehicle.toString()))
                .andExpect(jsonPath("$[0].registration").value("DUE 1"))
                .andExpect(jsonPath("$[0].services", hasSize(1)))
                .andExpect(jsonPath("$[0].services[0].name").value("Oil change"))
                .andExpect(jsonPath("$[0].services[0].dueMileage").value(47000))
                .andExpect(jsonPath("$[0].services[0].overdue").value(false)));
        Instant dueAt = Instant.parse(JsonPath.read(body, "$[0].dueAt"));
        org.assertj.core.api.Assertions.assertThat(Duration.between(Instant.now(), dueAt).toDays())
                .isBetween(165L, 167L);

        // At check-in: due by the odometer, not yet by the date.
        mvc.perform(get("/api/v1/service-due/vehicles/" + vehicle).param("mileage", "46600").with(mechanic))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Oil change"));
        mvc.perform(get("/api/v1/service-due/vehicles/" + vehicle).param("mileage", "43000").with(mechanic))
                .andExpect(jsonPath("$", hasSize(0)));
        // Mechanics don't get the list.
        mvc.perform(get("/api/v1/service-due").with(mechanic)).andExpect(status().isForbidden());
    }

    @Test
    void workFromALinkedCannedJobCountsWhateverItsCalled() throws Exception {
        String job = JsonPath.read(body(mvc.perform(post("/api/v1/canned-jobs").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Seasonal package\",\"description\":\"Seasonal check\",\"labourHours\":1,"
                        + "\"parts\":[]}"))
                .andExpect(status().isCreated())), "$.id");
        String rotation = JsonPath.read(body(mvc.perform(get("/api/v1/service-intervals").with(admin))), "$[1].id");
        mvc.perform(put("/api/v1/service-intervals/" + rotation).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tire rotation\",\"miles\":7500,\"months\":6,\"keywords\":[],"
                                + "\"cannedJobIds\":[\"" + job + "\"],\"version\":0}"))
                .andExpect(status().isOk());

        UUID vehicle = UUID.randomUUID();
        complete(vehicle, "Seasonal check", 10000, job);

        mvc.perform(get("/api/v1/service-due").param("days", "365").with(advisor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].services[0].name").value("Tire rotation"))
                .andExpect(jsonPath("$[0].services[0].cannedJobId").value(job));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private ResultActions interval(RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/v1/service-intervals").with(who).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** A job card for the vehicle taken to COMPLETED with one approved recommendation. */
    private void complete(UUID vehicle, String work, int mileage, String cannedJobId) throws Exception {
        String card = JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"DUE 1",
                                 "customerUserId":"%s","customerName":"Ana Silva","mileage":%d,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), vehicle, UUID.randomUUID(), mileage, tenant)))
                .andExpect(status().isCreated())), "$.id");
        act(mechanic, card, "findings", "{\"description\":\"Due for service\",\"severity\":\"LOW\"}");
        String item = JsonPath.read(body(act(advisor, card, "recommendations", """
                {"description":"%s","estimatedPartsCost":40,"estimatedLabourHours":0.5%s}"""
                .formatted(work, cannedJobId == null ? "" : ",\"cannedJobId\":\"" + cannedJobId + "\""))),
                "$.recommendations[0].id");
        act(advisor, card, "submit-for-approval", null);
        act(advisor, card, "recommendations/" + item + "/approve", null);
        act(mechanic, card, "start", null);
        act(mechanic, card, "work-logs", "{\"recommendationId\":\"%s\",\"description\":\"Done\"}".formatted(item));
        act(mechanic, card, "complete", null);
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
