package com.bayflow.workshop.estimates;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.bayflow.workshop.invoicing.InvoiceEmail;

/** The e-mail with the link to an estimate: the first one, and the reminders. */
final class EstimateEmail {

    record Content(String shopName, String shopPhone, String firstName, String vehicle, int items,
            BigDecimal total, String currency, String link, Instant expiresAt, ZoneId zone) {
    }

    record Rendered(String subject, String text, String html) {
    }

    private static final String INK = "#0f172a";
    private static final String MUTED = "#475569";
    private static final String ACCENT = "#0f766e";

    private EstimateEmail() {
    }

    static Rendered render(Content c, boolean reminder) {
        String subject = (reminder ? "Reminder: your estimate from " : "Your estimate from ") + c.shopName()
                + " is ready to review";
        String until = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US).withZone(c.zone()).format(c.expiresAt());
        String greeting = c.firstName() == null || c.firstName().isBlank() ? "Hi," : "Hi " + c.firstName() + ",";
        String what = c.items() == 1 ? "1 recommended item" : c.items() + " recommended items";
        String total = InvoiceEmail.money(c.total(), c.currency());
        String intro = reminder
                ? "Your estimate for the " + c.vehicle() + " is still waiting for your answer."
                : "We've looked at your " + c.vehicle() + ". Your estimate is ready: " + what + ", " + total
                        + " in all.";
        String ask = "Open it to see each item and its price, approve or decline each one, and sign. "
                + "We'll start on what you approve.";
        String call = c.shopPhone() == null || c.shopPhone().isBlank() ? ""
                : " Questions? Call us on " + c.shopPhone() + ".";
        String text = greeting + "\n\n" + intro + "\n" + ask + "\n\n" + c.link() + "\n\n"
                + "The link works until " + until + "." + call + "\n\n" + c.shopName() + "\n";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:" + INK
                + ";max-width:560px;line-height:1.5\">"
                + "<p>" + esc(greeting) + "</p>"
                + "<p>" + esc(intro) + "<br>" + esc(ask) + "</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + esc(c.link()) + "\" style=\"background:" + ACCENT
                + ";color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;font-weight:bold\">"
                + "Review your estimate</a></p>"
                + "<p style=\"color:" + MUTED + ";font-size:13px\">The link works until " + esc(until) + "."
                + esc(call) + "<br>If the button doesn't work, copy this address into your browser:<br>"
                + esc(c.link()) + "</p>"
                + "<p>" + esc(c.shopName()) + "</p></div>";
        return new Rendered(subject, text, html);
    }

    private static String esc(String value) {
        return value == null ? "" : htmlEscape(value);
    }
}
