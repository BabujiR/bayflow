package com.bayflow.auth.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.auth.mail.MailGateway;
import com.bayflow.auth.mail.MailOutbox;
import com.bayflow.auth.mail.OutgoingMail;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E-mail for the other Bayflow services (workshop-service e-mails invoices): auth-service owns
 * the SMTP settings and the outbox, so a queued e-mail is retried here like a reset link.
 */
@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Internal")
public class ServiceMailController {

    /** A finished e-mail: the calling service writes the subject and both bodies. */
    public record ServiceMailRequest(
            @NotBlank @Email @Size(max = 254) String to,
            @NotBlank @Size(max = 200) String senderName,
            @Email @Size(max = 254) String replyTo,
            @NotBlank @Size(max = 300) String subject,
            @NotBlank @Size(max = 200_000) String text,
            @NotBlank @Size(max = 500_000) String html) {
    }

    private final MailOutbox outbox;
    private final MailGateway gateway;
    private final TenantRepository tenants;

    public ServiceMailController(MailOutbox outbox, MailGateway gateway, TenantRepository tenants) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.tenants = tenants;
    }

    @PostMapping("/internal/tenants/{tenantId}/mail")
    @PreAuthorize("hasRole('SYSTEM')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Queue an e-mail for an organization (SYSTEM)",
            description = "Sent right away and retried if the mail server is down. 503 when e-mail is not set up.")
    public void send(@PathVariable UUID tenantId, @Valid @RequestBody ServiceMailRequest request) {
        if (!gateway.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail-unavailable", "Service Unavailable",
                    "E-mail is not set up on this server.");
        }
        if (!tenants.existsById(tenantId)) {
            throw ApiException.notFound("Workshop");
        }
        TenantContext.run(tenantId, () -> outbox.enqueue(new OutgoingMail(request.to().strip(),
                request.senderName().strip(), blankToNull(request.replyTo()), request.subject().strip(),
                request.text(), request.html())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
