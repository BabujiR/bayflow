package com.bayflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bayflow.notification.application.NotificationTexts.Text;
import com.bayflow.notification.application.Recipients.Audience;
import com.bayflow.notification.domain.EventType;

class NotificationTextsTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final UUID CARD = UUID.fromString("0d0d0d0d-0000-4000-8000-0000000000d1");

    @Test
    void everyEventHasWordsForEachAudience() {
        for (EventType type : EventType.values()) {
            for (Audience audience : Audience.values()) {
                Text text = NotificationTexts.of(event(type, "AB12 CDE"), audience, NEW_YORK);
                assertThat(text.title()).as(type + " " + audience).isNotBlank();
                assertThat(text.body()).as(type + " " + audience).isNotBlank();
                assertThat(text.link()).as(type + " " + audience).startsWith("/").doesNotStartWith("//");
            }
        }
    }

    @Test
    void customersHearAboutTheirCarAndStaffAboutTheRegistration() {
        assertThat(NotificationTexts.of(event(EventType.APPOINTMENT_CANCELLED, "AB12 CDE"), Audience.CUSTOMER,
                NEW_YORK).body()).isEqualTo("The workshop cancelled the Tue 24 Nov, 10:00 booking for AB12 CDE.");
        assertThat(NotificationTexts.of(event(EventType.APPOINTMENT_CANCELLED, "AB12 CDE"), Audience.DESK, NEW_YORK))
                .isEqualTo(new Text("Booking cancelled: AB12 CDE",
                        "The customer cancelled the Tue 24 Nov, 10:00 booking.", "/appointments"));
        assertThat(NotificationTexts.of(event(EventType.WORK_COMPLETED, null), Audience.CUSTOMER, NEW_YORK))
                .isEqualTo(new Text("Your car is ready", "The work on your car is complete. You can collect it.",
                        "/job-cards/" + CARD));
        assertThat(NotificationTexts.of(event(EventType.ESTIMATE_DECIDED, "AB12 CDE"), Audience.MECHANIC,
                NEW_YORK).body()).isEqualTo("1 item approved and ready to start.");
    }

    private static NotificationEvent event(EventType type, String registration) {
        return new NotificationEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), type, null, false,
                UUID.randomUUID(), CARD, registration, Instant.parse("2026-11-24T15:00:00Z"), null, 1, 0);
    }
}
