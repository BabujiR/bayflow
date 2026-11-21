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

    private static String page(String workshop, String body) {
        return """
                <!doctype html>
                <html><body style="margin:0;background:#f8fafc;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;\
                color:#0f172a">
                <div style="max-width:560px;margin:0 auto;padding:32px 20px">
                <div style="font-weight:700;font-size:20px;margin-bottom:20px">%s</div>
                <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:24px;line-height:1.55">
                %s
                </div>
                <p style="color:#94a3b8;font-size:12px;margin-top:20px">Sent by Ramplight for %s.</p>
                </div>
                </body></html>
                """.formatted(htmlEscape(workshop), body, htmlEscape(workshop));
    }
}
