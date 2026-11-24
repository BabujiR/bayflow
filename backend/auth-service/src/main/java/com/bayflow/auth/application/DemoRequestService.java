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
        DemoRequest saved = requests.save(DemoRequest.create(details, clientIp, clock.instant()));
        log.info("Demo request {} from {} ({} locations)", saved.getId(), saved.getShopName(), saved.getLocations());
        String to = properties.getDemo().getNotifyEmail();
        if (StringUtils.hasText(to) && gateway.isAvailable()) {
            // The outbox belongs to a workshop; platform mail goes under the default one.
            TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> mail.enqueue(notice(to.strip(), saved)));
        }
        return saved;
    }

    private static OutgoingMail notice(String to, DemoRequest r) {
        String name = r.getFirstName() + " " + r.getLastName();
        String subject = "Demo request: " + r.getShopName() + " (" + r.getLocations() + " locations)";
        String text = """
                New demo request from getramplight.com

                Name: %s
                Email: %s
                Phone: %s
                Shop: %s
                Type: %s
                Locations: %s
                City: %s

                %s
                """.formatted(name, r.getEmail(), r.getPhone(), r.getShopName(), r.getShopType(), r.getLocations(),
                orDash(r.getCity()), orDash(r.getMessage()));
        String html = """
                <!doctype html><html><body style="font-family:system-ui,sans-serif;color:#0f172a">
                <h2 style="margin:0 0 12px">New demo request</h2>
                <table cellpadding="4" style="border-collapse:collapse;font-size:15px">
                <tr><td style="color:#475569">Name</td><td>%s</td></tr>
                <tr><td style="color:#475569">Email</td><td><a href="mailto:%s">%s</a></td></tr>
                <tr><td style="color:#475569">Phone</td><td>%s</td></tr>
                <tr><td style="color:#475569">Shop</td><td>%s</td></tr>
                <tr><td style="color:#475569">Type</td><td>%s</td></tr>
                <tr><td style="color:#475569">Locations</td><td>%s</td></tr>
                <tr><td style="color:#475569">City</td><td>%s</td></tr>
                </table>
                <p style="white-space:pre-line">%s</p>
                </body></html>
                """.formatted(htmlEscape(name), htmlEscape(r.getEmail()), htmlEscape(r.getEmail()),
                htmlEscape(r.getPhone()), htmlEscape(r.getShopName()), htmlEscape(r.getShopType()),
                htmlEscape(r.getLocations()), htmlEscape(orDash(r.getCity())), htmlEscape(orDash(r.getMessage())));
        return new OutgoingMail(to, "Ramplight", r.getEmail(), subject, text, html);
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
    }
}
