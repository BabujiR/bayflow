package com.bayflow.workshop.reviews;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/** The e-mail asking a customer for a Google review, with a link to stop these. */
final class ReviewEmail {

    record Content(String shopName, String firstName, String vehicle, String reviewUrl, String stopUrl) {
    }

    record Rendered(String subject, String text, String html) {
    }

    private static final String INK = "#0f172a";
    private static final String MUTED = "#64748b";
    private static final String ACCENT = "#0f766e";

    private ReviewEmail() {
    }

    static Rendered render(Content c) {
        String subject = "How did we do? A quick review for " + c.shopName();
        String heading = c.firstName() == null || c.firstName().isBlank() ? "How did we do?"
                : "How did we do, " + c.firstName() + "?";
        String thanks = "Thanks for bringing "
                + (c.vehicle() == null || c.vehicle().isBlank() ? "your car" : "your " + c.vehicle()) + " to "
                + c.shopName() + ". If you have a minute, a Google review helps other drivers find us.";
        String stop = "Don't want these e-mails?";
        String text = heading + "\n\n" + thanks + "\n\nLeave a review: " + c.reviewUrl() + "\n\n" + c.shopName()
                + "\n\n" + stop + " Stop review requests: " + c.stopUrl() + "\n";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:" + INK
                + ";max-width:560px;line-height:1.5\">"
                + "<h2 style=\"font-size:20px;margin:0 0 12px\">" + esc(heading) + "</h2>"
                + "<p>" + esc(thanks) + "</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + esc(c.reviewUrl()) + "\" style=\"background:" + ACCENT
                + ";color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;font-weight:bold\">"
                + "Leave a review</a></p>"
                + "<p>" + esc(c.shopName()) + "</p>"
                + "<p style=\"color:" + MUTED + ";font-size:12px;margin-top:32px\">" + esc(stop) + " <a href=\""
                + esc(c.stopUrl()) + "\" style=\"color:" + MUTED + "\">Stop review requests</a></p></div>";
        return new Rendered(subject, text, html);
    }

    private static String esc(String value) {
        return value == null ? "" : htmlEscape(value);
    }
}
