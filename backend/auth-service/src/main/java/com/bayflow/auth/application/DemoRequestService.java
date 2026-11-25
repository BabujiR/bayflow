package com.bayflow.auth.application;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.DemoRequest;
import com.bayflow.auth.infrastructure.DemoRequestRepository;
import com.bayflow.auth.mail.MailGateway;
import com.bayflow.auth.mail.MailOutbox;
import com.bayflow.auth.mail.OutgoingMail;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;

/**
 * Demo requests from the public site: stored, and e-mailed to Ramplight's team when
 * BAYFLOW_DEMO_NOTIFY_EMAIL is set. Nothing is sent to the address typed in (so the form can't
 * be used to send e-mail to strangers).
 */
@Service
public class DemoRequestService {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestService.class);

    private final DemoRequestRepository requests;
    private final MailOutbox mail;
    private final MailGateway gateway;
    private final HourlyLimiter limiter;
    private final AuthProperties properties;
    private final Clock clock;

    public DemoRequestService(DemoRequestRepository requests, MailOutbox mail, MailGateway gateway,
            HourlyLimiter limiter, AuthProperties properties, Clock clock) {
        this.requests = requests;
        this.mail = mail;
        this.gateway = gateway;
        this.limiter = limiter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Not one transaction on purpose: the request is saved on its own, then the notice is queued in
     * a transaction of the default workshop (the mail outbox's row-level security needs one).
     */
    public DemoRequest submit(DemoRequest.Details details, String clientIp) {
        limiter.check("demo-ip|" + clientIp, properties.getDemo().getMaxPerHourPerIp());
        String email = details.email().strip().toLowerCase();
        // Asking again while the team is still on it joins that request (no second row). The form
        // answers the same either way, so it never tells anyone whether an address asked before.
        DemoRequest open = requests.findFirstByEmailAndStatusInOrderByCreatedAtDesc(email, DemoRequest.OPEN)
                .orElse(null);
        DemoRequest saved;
        boolean repeat = open != null;
        if (repeat) {
            open.askedAgain(details, clock.instant());
            saved = requests.save(open);
            log.info("Demo request {} asked again ({} repeats)", saved.getId(), saved.getRepeatCount());
        } else {
            saved = requests.save(DemoRequest.create(details, clientIp, clock.instant()));
            log.info("Demo request {} from {} ({} locations)", saved.getId(), saved.getShopName(),
                    saved.getLocations());
        }
        String to = properties.getDemo().getNotifyEmail();
        if (StringUtils.hasText(to) && gateway.isAvailable()) {
            // The outbox belongs to a workshop; platform mail goes under the default one.
            TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> mail.enqueue(notice(to.strip(), details, repeat)));
        }
        return saved;
    }

    /** What was sent this time (for a repeat too), so the team sees the latest details. */
    private static OutgoingMail notice(String to, DemoRequest.Details r, boolean repeat) {
        String name = r.firstName().strip() + " " + r.lastName().strip();
        String email = r.email().strip().toLowerCase();
        String subject = (repeat ? "Demo request (asked again): " : "Demo request: ") + r.shopName().strip()
                + " (" + r.locations() + " locations)";
        String heading = repeat ? "Asked again for a demo (their open request is updated)" : "New demo request";
        String text = """
                %s

                Name: %s
                Email: %s
                Phone: %s
                Shop: %s
                Locations: %s
                City: %s

                %s
                """.formatted(heading, name, email, r.phone().strip(), r.shopName().strip(), r.locations(),
                orDash(blankToNull(r.city())), orDash(blankToNull(r.message())));
        String html = """
                <!doctype html><html><body style="font-family:system-ui,sans-serif;color:#0f172a">
                <h2 style="margin:0 0 12px">%s</h2>
                <table cellpadding="4" style="border-collapse:collapse;font-size:15px">
                <tr><td style="color:#475569">Name</td><td>%s</td></tr>
                <tr><td style="color:#475569">Email</td><td><a href="mailto:%s">%s</a></td></tr>
                <tr><td style="color:#475569">Phone</td><td>%s</td></tr>
                <tr><td style="color:#475569">Shop</td><td>%s</td></tr>
                <tr><td style="color:#475569">Locations</td><td>%s</td></tr>
                <tr><td style="color:#475569">City</td><td>%s</td></tr>
                </table>
                <p style="white-space:pre-line">%s</p>
                </body></html>
                """.formatted(htmlEscape(heading), htmlEscape(name), htmlEscape(email), htmlEscape(email),
                htmlEscape(r.phone().strip()), htmlEscape(r.shopName().strip()), htmlEscape(r.locations()),
                htmlEscape(orDash(blankToNull(r.city()))), htmlEscape(orDash(blankToNull(r.message()))));
        return new OutgoingMail(to, "Ramplight", email, subject, text, html);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
    }
}
