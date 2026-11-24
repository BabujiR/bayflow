package com.bayflow.auth.api;

import static com.bayflow.auth.api.ConsoleController.requireConsole;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.application.InvitationService;
import com.bayflow.auth.application.InvitationService.Issued;
import com.bayflow.auth.domain.Invitation;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The Ramplight console: setup invites for new workshops (send, resend, revoke). */
@RestController
@RequestMapping("/api/v1/auth/platform/invitations")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ramplight console")
public class ConsoleInvitationsController {

    private static final int MAX_PAGE_SIZE = 100;

    public record InvitationResponse(UUID id, String email, String contactName, String shopName,
            UUID demoRequestId, Invitation.State state, Instant expiresAt, int sentCount, Instant lastSentAt,
            Instant createdAt, String createdBy, Instant acceptedAt, String workshopSlug, Instant revokedAt,
            String revokedBy) {

        static InvitationResponse from(Invitation i, Instant now) {
            return new InvitationResponse(i.getId(), i.getEmail(), i.getContactName(), i.getShopName(),
                    i.getDemoRequestId(), i.state(now), i.getExpiresAt(), i.getSentCount(), i.getLastSentAt(),
                    i.getCreatedAt(), i.getCreatedBy(), i.getAcceptedAt(), i.getWorkshopSlug(), i.getRevokedAt(),
                    i.getRevokedBy());
        }
    }

    /** {@code setupUrl} is shown this once: copy it when {@code emailed} is false. */
    public record IssuedResponse(InvitationResponse invitation, String setupUrl, boolean emailed) {
    }

    public record CreateInvitation(
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 120) String contactName,
            @Size(max = 120) String shopName,
            UUID demoRequestId) {
    }

    private final InvitationService invitations;
    private final Clock clock;

    public ConsoleInvitationsController(InvitationService invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Setup invites, newest first (state, q: email, contact or shop)")
    public PageResponse<InvitationResponse> list(@RequestParam(required = false) Invitation.State state,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireConsole();
        Instant now = clock.instant();
        Page<Invitation> found = invitations.search(state, q, Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return new PageResponse<>(found.getContent().stream().map(i -> InvitationResponse.from(i, now)).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Invite a workshop owner (e-mails the setup link when e-mail is set up)",
            description = "409 already-invited when the address has an invite that still works.")
    public IssuedResponse create(@Valid @RequestBody CreateInvitation request, Authentication auth) {
        requireConsole();
        return issued(invitations.create(CurrentUser.from(auth).username(), request.email(), request.contactName(),
                request.shopName(), request.demoRequestId()));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Send a new setup link (the old one stops working) valid for another 7 days")
    public IssuedResponse resend(@PathVariable UUID id, Authentication auth) {
        requireConsole();
        return issued(invitations.resend(CurrentUser.from(auth).username(), id));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Stop an unused setup link from working")
    public InvitationResponse revoke(@PathVariable UUID id, Authentication auth) {
        requireConsole();
        return InvitationResponse.from(invitations.revoke(CurrentUser.from(auth).username(), id), clock.instant());
    }

    private IssuedResponse issued(Issued issued) {
        return new IssuedResponse(InvitationResponse.from(issued.invitation(), clock.instant()), issued.setupUrl(),
                issued.emailed());
    }
}
