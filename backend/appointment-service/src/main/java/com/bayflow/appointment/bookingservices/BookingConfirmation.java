package com.bayflow.appointment.bookingservices;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.application.OutboxService;
import com.bayflow.appointment.config.AppointmentProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.appointment.integration.MailClient.Mail;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;

/**
 * The e-mails an online booker gets: booked, moved and cancelled (by them or by the shop). While
 * the booking can still change, each carries its private "change or cancel" link. Queued in the
 * outbox with the change, sent through auth-service right after it commits and retried if that
 * fails; the change never waits on the mail service.
 */
@Component
public class BookingConfirmation {

    /** Which e-mail. */
    public enum Kind {
        BOOKED("You're booked!", "You're booked at %s: %s"),
        MOVED_BY_YOU("Your booking is moved", "Your booking at %s is moved to %s"),
        MOVED_BY_SHOP("The shop moved your booking", "%s moved your booking to %s"),
        CANCELLED_BY_YOU("Your booking is cancelled", "Cancelled: your booking at %s on %s"),
        CANCELLED_BY_SHOP("The shop cancelled your booking", "%s cancelled your booking on %s");

        private final String headline;
        private final String subject;

        Kind(String headline, String subject) {
            this.headline = headline;
            this.subject = subject;
        }

        boolean cancelled() {
            return this == CANCELLED_BY_YOU || this == CANCELLED_BY_SHOP;
        }
    }

    public static final String MANAGE_PATH = "/book/manage";

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmation.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);

    private final OutboxService outbox;
    private final AppointmentProperties properties;

    public BookingConfirmation(OutboxService outbox, AppointmentProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
    }

    /**
     * {@code token}: the private link's token, or null for no link (cancelled bookings never
     * have one). Joins the caller's transaction when there is one. True when the e-mail was queued.
     */
    public boolean send(Workshop shop, LocationSettings location, boolean manyLocations, ZoneId zone, Appointment a,
            Kind kind, String token) {
        String name = shop.shownName();
        String when = WHEN.format(a.getScheduledAt().atZone(zone));
        String where = manyLocations ? location.name() + (location.address() == null ? "" : ", " + location.address())
                : location.address();
        String car = describe(a.getVehicle());
        String first = a.getCustomer().getName().split(" ")[0];
        String link = token == null || kind.cancelled() ? null : manageLink(shop.slug(), token);
        String subject = kind.subject.formatted(name, when);
        String after = link != null
                ? "Need to change or cancel? Use your private link (don't share it):\n" + link
                : kind.cancelled() ? "Want to come in another time? Book again on our website or call us."
                        : "Need to change or cancel? Reply to this e-mail or call the shop.";
        String text = """
                Hi %s,

                %s

                When: %s
                Service: %s
                Car: %s%s

                %s

                %s
                """.formatted(first, kind.headline, when, a.getServiceName(), car,
                where == null || where.isBlank() ? "" : "\nWhere: " + where, after, name);
        String button = link == null ? ""
                : "<p><a href=\"" + htmlEscape(link) + "\" style=\"display:inline-block;background:#0f172a;"
                        + "color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:8px;font-weight:600\">"
                        + "Change or cancel</a></p><p style=\"color:#94a3b8;font-size:12px\">This link is private: "
                        + "anyone with it can change your booking.</p>";
        String footer = link != null ? "" : "<p style=\"color:#475569;font-size:14px\">"
                + htmlEscape(after) + "</p>";
        String html = """
                <!doctype html><html><body style="margin:0;background:#f8fafc;font-family:system-ui,-apple-system,\
                'Segoe UI',sans-serif;color:#0f172a"><div style="max-width:560px;margin:0 auto;padding:32px 20px">
                <div style="font-weight:700;font-size:20px;margin-bottom:20px">%s</div>
                <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:24px;line-height:1.55">
                <p>Hi %s,</p><p style="font-size:18px;font-weight:700">%s</p>
                <table cellpadding="4" style="border-collapse:collapse;font-size:15px">
                <tr><td style="color:#475569">When</td><td><strong>%s</strong></td></tr>
                <tr><td style="color:#475569">Service</td><td>%s</td></tr>
                <tr><td style="color:#475569">Car</td><td>%s</td></tr>%s
                </table>%s%s
                </div><p style="color:#94a3b8;font-size:12px;margin-top:20px">Sent by Ramplight for %s.</p></div>
                </body></html>
                """.formatted(htmlEscape(name), htmlEscape(first), htmlEscape(kind.headline), htmlEscape(when),
                htmlEscape(a.getServiceName()), htmlEscape(car), where == null || where.isBlank() ? ""
                        : "<tr><td style=\"color:#475569\">Where</td><td>" + htmlEscape(where) + "</td></tr>",
                button, footer, htmlEscape(name));
        try {
            outbox.enqueueMail(a.getId(), new Mail(a.getCustomer().getEmail(), name, null, subject, text, html));
            return true;
        } catch (RuntimeException e) {
            log.warn("Booking e-mail {} for {} not queued: {}", kind, a.getId(), e.getMessage());
            return false;
        }
    }

    /** https://<slug>.<site host>[:port]/book/manage?token=… */
    String manageLink(String slug, String token) {
        URI site = URI.create(properties.getSiteUrl());
        String port = site.getPort() == -1 ? "" : ":" + site.getPort();
        return site.getScheme() + "://" + slug + "." + site.getHost() + port + MANAGE_PATH + "?token=" + token;
    }

    /** "2019 Honda Civic", or "VIN …A01234" when only the VIN is known. */
    static String describe(Vehicle v) {
        if (v.getMake() != null) {
            return (v.getYear() > 0 ? v.getYear() + " " : "") + v.getMake() + " " + v.getModel();
        }
        return "VIN " + v.getVin();
    }
}
