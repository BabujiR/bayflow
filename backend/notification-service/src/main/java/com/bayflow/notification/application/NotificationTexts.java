package com.bayflow.notification.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.bayflow.notification.application.Recipients.Audience;

/**
 * The words of each notification, in English, with times in the location's time zone. Plain
 * text: the web app shows it as text, never as HTML.
 */
final class NotificationTexts {

    /** Title, body and where clicking it goes in the web app. */
    record Text(String title, String body, String link) {
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private NotificationTexts() {
    }

    static Text of(NotificationEvent event, Audience audience, ZoneId zone) {
        String car = event.registration() == null || event.registration().isBlank() ? "your car" : event.registration();
        String reg = event.registration() == null || event.registration().isBlank() ? "a car" : event.registration();
        String when = when(event.scheduledAt(), zone);
        String jobCard = "/job-cards/" + event.subjectId();
        return switch (event.type()) {
            case APPOINTMENT_BOOKED -> audience == Audience.CUSTOMER
                    ? new Text("Service booked", "The workshop booked " + car + " in for " + when + ".", "/appointments")
                    : new Text("New booking: " + reg, "Booked for " + when + ".", "/appointments");
            case APPOINTMENT_RESCHEDULED -> audience == Audience.CUSTOMER
                    ? new Text("Your booking was moved", capitalize(car) + " is now booked for " + when + ".",
                            "/appointments")
                    : new Text("Booking moved: " + reg, "Now " + when + previously(event, zone) + ".", "/appointments");
            case APPOINTMENT_CANCELLED -> audience == Audience.CUSTOMER
                    ? new Text("Your booking was cancelled",
                            "The workshop cancelled the " + when + " booking for " + car + ".", "/appointments")
                    : new Text("Booking cancelled: " + reg,
                            "The customer cancelled the " + when + " booking.", "/appointments");
            case APPOINTMENT_CONFIRMED -> new Text("Booking confirmed",
                    capitalize(car) + " on " + when + " is confirmed. See you then!", "/appointments");
            case VEHICLE_CHECKED_IN -> audience == Audience.CUSTOMER
                    ? new Text("Your car is checked in",
                            capitalize(car) + " is at the workshop. You'll hear from us after the inspection.",
                            "/job-cards")
                    : new Text("Car checked in: " + reg, "Ready for inspection.", "/my-jobs");
            case ESTIMATE_READY -> new Text("Your estimate is ready",
                    "Review and approve the recommended work for " + car + ".", jobCard);
            case ESTIMATE_DECIDED -> audience == Audience.MECHANIC
                    ? new Text("Work approved: " + reg, items(event.approved()) + " approved and ready to start.",
                            jobCard)
                    : new Text("Estimate decided: " + reg,
                            event.approved() + " approved, " + event.declined() + " declined.", jobCard);
            case WORK_COMPLETED -> new Text("Your car is ready",
                    "The work on " + car + " is complete. You can collect it.", jobCard);
        };
    }

    private static String when(Instant at, ZoneId zone) {
        return at == null ? "the booked time" : WHEN.format(at.atZone(zone));
    }

    private static String previously(NotificationEvent event, ZoneId zone) {
        return event.previousScheduledAt() == null ? "" : " (was " + when(event.previousScheduledAt(), zone) + ")";
    }

    private static String items(int count) {
        return count == 1 ? "1 item" : count + " items";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
