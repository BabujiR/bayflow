package com.bayflow.appointment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Two workshops share the tables: each sees only its own customers and vehicles, rows are
 * stamped with the caller's workshop, and VINs are unique per workshop, not globally.
 */
class MultiTenantAppointmentIT extends IntegrationTestBase {

    private static final UUID OTHER = UUID.fromString("0b0b0b0b-0000-4000-8000-000000000002");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theSameCarCanBeRegisteredOnceInEachWorkshop() throws Exception {
        String vin = randomVin();
        RequestPostProcessor here = customerWithProfile(Tenants.DEFAULT_TENANT_ID, "Here Customer");
        RequestPostProcessor there = customerWithProfile(OTHER, "There Customer");

        String hereId = id(addVehicle(here, vin).andExpect(status().isCreated()));
        String thereId = id(addVehicle(there, vin).andExpect(status().isCreated()));

        assertThat(tenantOf("apt_vehicles", hereId)).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(tenantOf("apt_vehicles", thereId)).isEqualTo(OTHER);

        // ...but only once within a workshop.
        RequestPostProcessor thereAgain = customerWithProfile(OTHER, "Another Customer");
        addVehicle(thereAgain, vin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:vin-taken"));
    }

    @Test
    void advisorsOnlySeeTheirOwnWorkshop() throws Exception {
        String marker = "Isolated " + UUID.randomUUID().toString().substring(0, 8);
        RequestPostProcessor here = customerWithProfile(Tenants.DEFAULT_TENANT_ID, marker);
        String vehicleId = id(addVehicle(here, randomVin()).andExpect(status().isCreated()));

        RequestPostProcessor hereAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID(), Tenants.DEFAULT_TENANT_ID);
        RequestPostProcessor otherAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID(), OTHER);

        mvc.perform(get("/api/v1/customers").param("q", marker).with(hereAdvisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/customers").param("q", marker).with(otherAdvisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/vehicles/" + vehicleId).with(hereAdvisor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vehicles/" + vehicleId).with(otherAdvisor)).andExpect(status().isNotFound());
    }

    private RequestPostProcessor customerWithProfile(UUID tenant, String name) throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID(), tenant);
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s@example.com"}
                                """.formatted(name, UUID.randomUUID())))
                .andExpect(status().isCreated());
        return customer;
    }

    private ResultActions addVehicle(RequestPostProcessor customer, String vin) throws Exception {
        return mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vin":"%s","registration":"MT 1","make":"Honda","model":"Civic","year":2020,"mileage":1000}
                        """.formatted(vin)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID tenantOf(String table, String id) {
        return jdbc.queryForObject("select tenant_id from " + table + " where id = ?::uuid", UUID.class, id);
    }

    private static String randomVin() {
        String chars = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
        StringBuilder vin = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            vin.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return vin.toString();
    }
}
