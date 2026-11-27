package com.bayflow.workshop.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.estimates.EstimateLinks;
import com.bayflow.workshop.integration.AuthServiceClient.LocationProfile;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.bayflow.workshop.integration.AuthServiceClient.WorkshopProfile;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.jayway.jsonpath.JsonPath;

/** The customer approves an estimate from an e-mailed link: no account, every item, a signature. */
@TestPropertySource(properties = "bayflow.workshop.estimates.send-on-submit=true")
class EstimateApprovalIT extends IntegrationTestBase {

    private static final Pattern LINK = Pattern.compile("demo\\.localhost:3000/estimate\\?token=([A-Za-z0-9_-]{43})");

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");

    @Autowired
    private EstimateLinks links;

    @BeforeEach
    void shop() {
        Mockito.reset(authService);
        Mockito.doReturn(new WorkshopProfile("demo", "Demo Garage LLC", "Demo Garage", "555-0100", null, null))
                .when(authService).workshop(ArgumentMatchers.any());
        Mockito.doReturn(new LocationProfile("Main", "America/New_York", "555-0101", null, null))
                .when(authService).location(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(new BillingContact(UUID.randomUUID(), "Jane Doe", "jane@example.com", null, "Honda",
                "Civic", 2019)).when(billingContacts).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void theCustomerApprovesAndSignsFromTheLink() throws Exception {
        String id = awaitingApproval();
        Mail mail = lastMail();
        assertThat(mail.to()).isEqualTo("jane@example.com");
        assertThat(mail.subject()).isEqualTo("Your estimate from Demo Garage is ready to review");
        String token = token(mail);

        String body = view(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Demo Garage"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.vehicle").value("2019 Honda Civic (BFL 1234)"))
                .andExpect(jsonPath("$.canDecide").value(true))
                .andExpect(jsonPath("$.findings[0].description").value("Front pads at 2 mm"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String pads = JsonPath.read(body, "$.items[0].id");
        String wipers = JsonPath.read(body, "$.items[1].id");
        mvc.perform(get("/api/v1/job-cards/" + id + "/estimate-link").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("jane@example.com"))
                .andExpect(jsonPath("$.firstViewedAt").isNotEmpty())
                .andExpect(jsonPath("$.open").value(true));

        // Every item, a name and a real signature.
        decide(token, List.of(pads + ":true"), "Jane Doe", signature())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:decide-every-item"));
        decide(token, List.of(pads + ":true", wipers + ":false"), "Jane Doe", "data:image/png;base64,AAAA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-signature"));

        decide(token, List.of(pads + ":true", wipers + ":false"), "Jane Doe", signature())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.canDecide").value(false))
                .andExpect(jsonPath("$.signerName").value("Jane Doe"))
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[1].status").value("DECLINED"));

        mvc.perform(get("/api/v1/job-cards/" + id).with(advisor))
                .andExpect(jsonPath("$.status").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.recommendations[0].decidedBy").value("Jane Doe (signed online)"));
        mvc.perform(get("/api/v1/job-cards/" + id + "/estimate-link").with(advisor))
                .andExpect(jsonPath("$.signed").value(true))
                .andExpect(jsonPath("$.signerName").value("Jane Doe"));
        mvc.perform(get("/api/v1/job-cards/" + id + "/estimate-link/signature").with(advisor))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        // Answered once; the page still shows what was decided.
        decide(token, List.of(pads + ":false", wipers + ":false"), "Jane Doe", signature())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:estimate-decided"));
        view(token).andExpect(status().isOk()).andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void sendingAgainReplacesTheLink() throws Exception {
        String id = awaitingApproval();
        String first = token(lastMail());

        mvc.perform(post("/api/v1/job-cards/" + id + "/estimate-link").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"jane.work@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("jane.work@example.com"));
        String second = token(lastMail());

        view(first).andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:link-replaced"));
        view(second).andExpect(status().isOk());
    }

    @Test
    void remindersCarryANewLinkAndStopOnceAnswered() throws Exception {
        String id = awaitingApproval();
        String first = token(lastMail());
        dueNow(id);

        assertThat(links.sendDueReminders()).isEqualTo(1);
        Mail reminder = lastMail();
        assertThat(reminder.subject()).startsWith("Reminder: your estimate from Demo Garage");
        String second = token(reminder);
        assertThat(second).isNotEqualTo(first);
        // Both links work: the customer may open either e-mail.
        view(first).andExpect(status().isOk());
        String body = view(second).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/job-cards/" + id + "/estimate-link").with(advisor))
                .andExpect(jsonPath("$.remindersSent").value(1));

        // Answered at the desk: no more reminders.
        List<String> items = JsonPath.read(body, "$.items[*].id");
        for (String item : items) {
            mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations/" + item + "/approve").with(advisor))
                    .andExpect(status().isOk());
        }
        dueNow(id);
        assertThat(links.sendDueReminders()).isZero();
        assertThat(ownerJdbc().queryForObject("select count(*) from bayflow.ws_estimate_links where job_card_id = ?"
                + " and next_reminder_at is not null", Integer.class, UUID.fromString(id))).isZero();
    }

    @Test
    void aLinkIsOnlyForAnEstimateWaitingForTheCustomer() throws Exception {
        String id = inspected();
        mvc.perform(post("/api/v1/job-cards/" + id + "/estimate-link").with(advisor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-awaiting-approval"));
        mvc.perform(get("/api/v1/job-cards/" + id + "/estimate-link").with(advisor))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/job-cards/" + id + "/estimate-link").with(mechanic))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownLinksAreNotFound() throws Exception {
        view("x".repeat(43)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:link-not-found"));
        view("short").andExpect(status().isNotFound());
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Inspected, two recommendations: replace pads (approve), wipers. */
    private String inspected() throws Exception {
        String body = mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352",
                                 "registration":"BFL 1234","customerUserId":null,"customerName":"Jane Doe",
                                 "mileage":12480,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), Tenants.DEFAULT_TENANT_ID)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/job-cards/" + id + "/findings").with(mechanic)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Front pads at 2 mm\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isOk());
        for (String item : List.of("Replace front pads", "Replace wiper blades")) {
            mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations").with(advisor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"%s\",\"estimatedPartsCost\":40.00,\"estimatedLabourHours\":0.5}"
                            .formatted(item)))
                    .andExpect(status().isOk());
        }
        return id;
    }

    /** Submitted: the link goes out to the customer's address. */
    private String awaitingApproval() throws Exception {
        String id = inspected();
        mvc.perform(post("/api/v1/job-cards/" + id + "/submit-for-approval").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));
        return id;
    }

    private void dueNow(String id) {
        ownerJdbc().update("update bayflow.ws_estimate_links set next_reminder_at = now() - interval '1 minute'"
                + " where job_card_id = ? and replaced_at is null and decided_at is null"
                + " and next_reminder_at is not null", UUID.fromString(id));
    }

    private ResultActions view(String token) throws Exception {
        return mvc.perform(post("/api/v1/estimate-approval/view").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    /** {@code decisions}: "itemId:true|false". */
    private ResultActions decide(String token, List<String> decisions, String name, String signature)
            throws Exception {
        String items = String.join(",", decisions.stream().map(d -> {
            String[] parts = d.split(":");
            return "{\"itemId\":\"" + parts[0] + "\",\"approve\":" + parts[1] + "}";
        }).toList());
        return mvc.perform(post("/api/v1/estimate-approval/decide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"decisions\":[%s],\"signerName\":\"%s\",\"signature\":\"%s\"}"
                        .formatted(token, items, name, signature)));
    }

    private Mail lastMail() {
        ArgumentCaptor<Mail> sent = ArgumentCaptor.forClass(Mail.class);
        Mockito.verify(authService, Mockito.atLeastOnce()).send(ArgumentMatchers.eq(Tenants.DEFAULT_TENANT_ID),
                sent.capture());
        return sent.getValue();
    }

    private static String token(Mail mail) {
        Matcher link = LINK.matcher(mail.text());
        assertThat(link.find()).as("link in: " + mail.text()).isTrue();
        return link.group(1);
    }

    /** A drawn line, as the signature pad sends it. */
    private static String signature() throws Exception {
        BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.drawLine(10, 80, 290, 20);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
    }
}
