package com.bayflow.workshop.invoicing;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.integration.AuthServiceClient;
import com.bayflow.workshop.integration.AuthServiceClient.LocationProfile;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.bayflow.workshop.integration.AuthServiceClient.MailUnavailableException;
import com.bayflow.workshop.integration.AuthServiceClient.WorkshopProfile;
import com.bayflow.workshop.invoicing.InvoiceEmail.Letterhead;
import com.bayflow.workshop.invoicing.InvoiceEmail.Rendered;

/**
 * E-mails issued invoices to customers. workshop-service writes the e-mail (it knows the
 * invoice); auth-service sends it (it owns the mail server settings and retries).
 */
@Service
public class InvoiceMailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMailService.class);

    private final InvoiceRepository invoices;
    private final AuthServiceClient auth;
    private final WorkshopProperties properties;
    private final Clock clock;

    public InvoiceMailService(InvoiceRepository invoices, AuthServiceClient auth, WorkshopProperties properties,
            Clock clock) {
        this.invoices = invoices;
        this.auth = auth;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sends the invoice to {@code to}, or to the customer's address when blank. */
    @Transactional
    public Invoice email(CurrentUser user, UUID id, String to) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
        invoice.requireSendable("e-mail");
        invoice.loadDetails();
        String address = to == null || to.isBlank() ? invoice.getCustomerEmail() : to.strip();
        if (address == null || address.isBlank()) {
            throw ApiException.badRequest("no-email-address",
                    "This customer has no e-mail address on the invoice. Enter one to send it to.");
        }
        UUID tenant = TenantContext.require();
        WorkshopProfile workshop;
        LocationProfile location;
        try {
            workshop = auth.workshop(tenant);
            location = auth.location(tenant, invoice.getLocationId());
            if (workshop == null) {
                throw new IllegalStateException("auth-service returned no workshop");
            }
        } catch (RuntimeException e) {
            log.warn("Could not read the workshop details for invoice {}: {}", id, e.toString());
            throw unavailable("settings-unavailable",
                    "The workshop's details could not be read. Try again in a moment.");
        }
        Rendered email = InvoiceEmail.render(invoice, letterhead(invoice, workshop, location));
        String replyTo = firstText(location == null ? null : location.email(), workshop.email());
        try {
            auth.send(tenant, new Mail(address, workshop.shownName(), replyTo, email.subject(), email.text(),
                    email.html()));
        } catch (MailUnavailableException e) {
            throw unavailable("mail-unavailable",
                    "E-mail is not set up on this server. Print the invoice or ask your administrator.");
        } catch (RuntimeException e) {
            log.warn("Could not queue the e-mail of invoice {}: {}", id, e.toString());
            throw unavailable("mail-failed", "The e-mail could not be sent. Try again in a moment.");
        }
        invoice.markEmailed(address, user.username(), clock.instant());
        Invoice saved = invoices.saveAndFlush(invoice);
        saved.loadDetails();
        log.info("Invoice {} e-mailed by {}", saved.getNumber(), user.username());
        return saved;
    }

    private Letterhead letterhead(Invoice invoice, WorkshopProfile workshop, LocationProfile location) {
        String name = workshop.shownName();
        ZoneId zone = zone(location == null ? null : location.timeZone());
        // Customers with an account can open it in the app; walk-ins without one only get the e-mail.
        String link = invoice.getCustomerUserId() == null || workshop.slug() == null ? null
                : site(workshop.slug()) + "/invoices/" + invoice.getId();
        if (location == null) {
            return new Letterhead(name, null, workshop.address(), workshop.phone(), workshop.email(), zone, link);
        }
        return new Letterhead(name, location.name(), firstText(location.address(), workshop.address()),
                firstText(location.phone(), workshop.phone()), firstText(location.email(), workshop.email()), zone,
                link);
    }

    /** https://<slug>.<site host>[:port], like the links in auth-service's e-mails. */
    private String site(String slug) {
        URI site = URI.create(properties.getSiteUrl());
        String port = site.getPort() == -1 ? "" : ":" + site.getPort();
        return site.getScheme() + "://" + slug + "." + site.getHost() + port;
    }

    private static ZoneId zone(String id) {
        try {
            return id == null || id.isBlank() ? ZoneOffset.UTC : ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
    }

    private static ApiException unavailable(String type, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, type, "Service Unavailable", detail);
    }
}
