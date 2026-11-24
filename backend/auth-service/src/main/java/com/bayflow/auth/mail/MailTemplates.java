package com.bayflow.auth.mail;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/** The e-mails auth-service sends, as plain text and HTML (both: HTML-only mail lands in spam more). */
public final class MailTemplates {

    private MailTemplates() {
    }

    /** The "forgot password" e-mail with its single-use link. */
    public static OutgoingMail resetLink(String to, String workshop, String replyTo, String username, String link,
            long minutes) {
        String subject = "Reset your " + workshop + " password";
        String text = """
                Hello %s,

                Someone asked to reset the password of your %s account (username: %s).
                Choose a new password here (the link works for %d minutes and only once):

                %s

                Didn't ask for this? Ignore this e-mail; your password stays the same.

                %s
                """.formatted(username, workshop, username, minutes, link, workshop);
        String html = page(workshop, """
                <p>Hello %s,</p>
                <p>Someone asked to reset the password of your %s account (username: <strong>%s</strong>).</p>
                <p style="margin:28px 0"><a href="%s" style="background:#0f766e;color:#ffffff;padding:12px 22px;\
                border-radius:8px;text-decoration:none;font-weight:600;display:inline-block">Reset your password</a></p>
                <p style="color:#475569;font-size:14px">The link works for %d minutes and only once. If the button
                doesn't work, copy this address into your browser:<br><span style="word-break:break-all">%s</span></p>
                <p style="color:#475569;font-size:14px">Didn't ask for this? Ignore this e-mail; your password stays
                the same.</p>
                """.formatted(htmlEscape(username), htmlEscape(workshop), htmlEscape(username), htmlEscape(link),
                minutes, htmlEscape(link)));
        return new OutgoingMail(to, workshop, replyTo, subject, text, html);
    }

    /** Sent after the password was changed through a reset link. */
    public static OutgoingMail passwordChanged(String to, String workshop, String replyTo, String username) {
        String subject = "Your " + workshop + " password was changed";
        String text = """
                Hello %s,

                The password of your %s account (username: %s) was just changed, and you were signed out
                on your other devices.

                Wasn't you? Contact %s straight away.

                %s
                """.formatted(username, workshop, username, workshop, workshop);
        String html = page(workshop, """
                <p>Hello %s,</p>
                <p>The password of your %s account (username: <strong>%s</strong>) was just changed, and you were
                signed out on your other devices.</p>
                <p><strong>Wasn't you?</strong> Contact %s straight away.</p>
                """.formatted(htmlEscape(username), htmlEscape(workshop), htmlEscape(username), htmlEscape(workshop)));
        return new OutgoingMail(to, workshop, replyTo, subject, text, html);
    }

    /** A setup invite from Ramplight's team: the link creates the workshop and its admin. */
    public static OutgoingMail invitation(String to, String contactName, String shopName, String link, long days) {
        String greeting = contactName == null ? "Hello," : "Hello " + contactName + ",";
        String shop = shopName == null ? "your workshop" : shopName;
        String subject = "Set up " + shop + " on Ramplight";
        String text = """
                %s

                Your Ramplight account is ready to set up. Open this link to create %s, choose its
                address and set your admin password (the link works for %d days and only once):

                %s

                Questions? Just reply to this e-mail.

                Ramplight
                """.formatted(greeting, shop, days, link);
        String html = page("Ramplight", """
                <p>%s</p>
                <p>Your Ramplight account is ready to set up. Open the link to create <strong>%s</strong>, choose
                its address and set your admin password.</p>
                <p style="margin:28px 0"><a href="%s" style="background:#2b3bd1;color:#ffffff;padding:12px 22px;\
                border-radius:8px;text-decoration:none;font-weight:600;display:inline-block">Set up your workshop</a></p>
                <p style="color:#475569;font-size:14px">The link works for %d days and only once. If the button
                doesn't work, copy this address into your browser:<br><span style="word-break:break-all">%s</span></p>
                <p style="color:#475569;font-size:14px">Questions? Just reply to this e-mail.</p>
                """.formatted(htmlEscape(greeting), htmlEscape(shop), htmlEscape(link), days, htmlEscape(link)),
                "Sent by Ramplight.");
        return new OutgoingMail(to, "Ramplight", null, subject, text, html);
    }

    /**
     * The setup link is about to expire, unused. The link itself can't be repeated (only a hash
     * is kept): it points to the first e-mail, and a reply reaches Ramplight's team.
     */
    public static OutgoingMail inviteReminder(String to, String contactName, String shopName,
            java.time.Instant sentAt, java.time.Instant expiresAt, String replyTo) {
        java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter
                .ofPattern("MMMM d, yyyy", java.util.Locale.US).withZone(java.time.ZoneOffset.UTC);
        String greeting = contactName == null ? "Hello," : "Hello " + contactName + ",";
        String shop = shopName == null ? "your workshop" : shopName;
        String firstSubject = "Set up " + shop + " on Ramplight";
        String subject = "Your Ramplight setup link expires " + day.format(expiresAt);
        String text = """
                %s

                On %s we sent you a link to set up %s on Ramplight. It hasn't been used yet and
                stops working on %s.

                Look for the e-mail "%s". Can't find it, or need more time? Reply to this e-mail
                and we'll send you a new link.

                Ramplight
                """.formatted(greeting, day.format(sentAt), shop, day.format(expiresAt), firstSubject);
        String html = page("Ramplight", """
                <p>%s</p>
                <p>On %s we sent you a link to set up <strong>%s</strong> on Ramplight. It hasn't been used yet
                and stops working on <strong>%s</strong>.</p>
                <p>Look for the e-mail &ldquo;%s&rdquo;. Can't find it, or need more time? Reply to this e-mail
                and we'll send you a new link.</p>
                """.formatted(htmlEscape(greeting), day.format(sentAt), htmlEscape(shop), day.format(expiresAt),
                htmlEscape(firstSubject)), "Sent by Ramplight.");
        return new OutgoingMail(to, "Ramplight", replyTo, subject, text, html);
    }

    private static String page(String workshop, String body) {
        return page(workshop, body, "Sent by Ramplight for " + htmlEscape(workshop) + ".");
    }

    private static String page(String workshop, String body, String footer) {
        return """
                <!doctype html>
                <html><body style="margin:0;background:#f8fafc;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\
                color:#0f172a">
                <div style="max-width:560px;margin:0 auto;padding:32px 20px">
                <div style="font-weight:700;font-size:20px;margin-bottom:20px">%s</div>
                <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:24px;line-height:1.55">
                %s
                </div>
                <p style="color:#94a3b8;font-size:12px;margin-top:20px">%s</p>
                </div>
                </body></html>
                """.formatted(htmlEscape(workshop), body, footer);
    }
}
