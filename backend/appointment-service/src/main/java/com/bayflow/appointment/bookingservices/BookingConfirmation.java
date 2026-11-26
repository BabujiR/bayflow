package com.bayflow.appointment.bookingservices;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.appointment.integration.MailClient;
import com.bayflow.appointment.integration.MailClient.Mail;

/**
 * The e-mail a customer gets after booking online. Best effort: the booking stands if it can't be
 * sent (no mail server set up, or auth-service unreachable); that is logged, never shown.
 */
@Component
public class BookingConfirmation {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmation.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US);

    private final MailClient mail;

    public BookingConfirmation(MailClient mail) {
        this.mail = mail;
    }

    /** {@code shop}: the name customers know. True when the e-mail was handed over. */
    public boolean send(UUID tenantId, String shop, LocationSettings location, boolean manyLocations, ZoneId zone,
            Appointment a) {
        String when = WHEN.format(a.getScheduledAt().atZone(zone));
        String where = manyLocations ? location.name() + (location.address() == null ? "" : ", " + location.address())
                : location.address();
        String car = describe(a.getVehicle());
        String first = a.getCustomer().getName().split(" ")[0];
        String subject = "You're booked at " + shop + ": " + when;
        String text = """
                Hi %s,

                You're booked at %s.

                When: %s
                Service: %s
                Car: %s%s

                Need to change or cancel? Reply to this e-mail or call the shop.

                %s
                """.formatted(first, shop, when, a.getServiceName(), car,
                where == null || where.isBlank() ? "" : "\nWhere: " + where, shop);
        String html = """
                <!doctype html><html><body style="margin:0;background:#f8fafc;font-family:system-ui,-apple-system,\
                'Segoe UI',sans-serif;color:#0f172a"><div style="max-width:560px;margin:0 auto;padding:32px 20px">
                <div style="font-weight:700;font-size:20px;margin-bottom:20px">%s</div>
                <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:24px;line-height:1.55">
                <p>Hi %s,</p><p style="font-size:18px;font-weight:700">You're booked!</p>
                <table cellpadding="4" style="border-collapse:collapse;font-size:15px">
                <tr><td style="color:#475569">When</td><td><strong>%s</strong></td></tr>
                <tr><td style="color:#475569">Service</td><td>%s</td></tr>
                <tr><td style="color:#475569">Car</td><td>%s</td></tr>%s
                </table>
                <p style="color:#475569;font-size:14px">Need to change or cancel? Reply to this e-mail or call the shop.</p>
                </div><p style="color:#94a3b8;font-size:12px;margin-top:20px">Sent by Ramplight for %s.</p></div>
                </body></html>
                """.formatted(htmlEscape(shop), htmlEscape(first), htmlEscape(when), htmlEscape(a.getServiceName()),
                htmlEscape(car), where == null || where.isBlank() ? ""
                        : "<tr><td style=\"color:#475569\">Where</td><td>" + htmlEscape(where) + "</td></tr>",
                htmlEscape(shop));
        try {
            mail.send(tenantId, new Mail(a.getCustomer().getEmail(), shop, null, subject, text, html));
            return true;
        } catch (RuntimeException e) {
            log.warn("Booking confirmation for {} not sent: {}", a.getId(), e.getMessage());
            return false;
        }
    }

    /** "2019 Honda Civic", or "VIN …A01234" when only the VIN is known. */
    static String describe(Vehicle v) {
        if (v.getMake() != null) {
            return (v.getYear() > 0 ? v.getYear() + " " : "") + v.getMake() + " " + v.getModel();
        }
        return "VIN " + v.getVin();
    }
}
