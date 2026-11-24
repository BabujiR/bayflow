package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.DemoRequest;
import com.bayflow.auth.domain.Invitation;
import com.bayflow.auth.infrastructure.DemoRequestRepository;
import com.bayflow.auth.infrastructure.InvitationRepository;
import com.bayflow.auth.mail.MailGateway;
import com.bayflow.auth.mail.MailOutbox;
import com.bayflow.auth.mail.MailTemplates;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;

/**
 * Setup invites: Ramplight's team invites a workshop owner from the console; the owner opens the
 * link at {@code <site>/setup?invite=…} and creates the workshop. Each link works once and for
 * {@code bayflow.auth.signup.invite-ttl} (7 days); resending replaces it.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    /** The public page the link opens. */
    public static final String SETUP_PATH = "/setup";

    /**
     * A new or resent invite. {@code setupUrl} is shown once (only a hash is kept), so the team
     * can copy it when e-mail is not set up; {@code emailed}: an e-mail with it was queued.
     */
    public record Issued(Invitation invitation, String setupUrl, boolean emailed) {
    }

    private final InvitationRepository invitations;
    private final DemoRequestRepository demos;
    private final MailOutbox mail;
    private final MailGateway gateway;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public InvitationService(InvitationRepository invitations, DemoRequestRepository demos, MailOutbox mail,
            MailGateway gateway, AuthProperties properties, Clock clock, PlatformTransactionManager transactions) {
        this.invitations = invitations;
        this.demos = demos;
        this.mail = mail;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public Page<Invitation> search(Invitation.State state, String query, int page, int size) {
        String like = query == null || query.isBlank() ? null
                : "%" + query.strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        Invitation.Status status = state == null ? null : switch (state) {
            case PENDING, EXPIRED -> Invitation.Status.PENDING;
            case ACCEPTED -> Invitation.Status.ACCEPTED;
            case REVOKED -> Invitation.Status.REVOKED;
        };
        int expiry = state == Invitation.State.PENDING ? 1 : state == Invitation.State.EXPIRED ? 2 : 0;
        return invitations.search(status, expiry, clock.instant(), like, PageRequest.of(page, size));
    }

    /**
     * Invites {@code email}; from a demo request when {@code demoRequestId} is set (it becomes
     * Invited). The invite is saved first, then the e-mail queued (the mail outbox belongs to a
     * workshop, so platform mail goes under the default one, in its own transaction).
     */
    public Issued create(String by, String email, String contactName, String shopName, UUID demoRequestId) {
        String token = SecureTokens.newToken();
        Invitation saved = tx.execute(status -> {
            Instant now = clock.instant();
            String address = email.strip().toLowerCase();
            if (invitations.existsByEmailAndStatusAndExpiresAtAfter(address, Invitation.Status.PENDING, now)) {
                throw ApiException.conflict("already-invited",
                        address + " already has a setup invite that still works. Resend it instead.");
            }
            if (demoRequestId != null) {
                DemoRequest demo = demos.findById(demoRequestId)
                        .orElseThrow(() -> ApiException.notFound("Demo request"));
                if (demo.getStatus() == DemoRequest.Status.ONBOARDED) {
                    throw ApiException.badRequest("already-onboarded", "This shop is already set up.");
                }
                demo.invited(by, now);
            }
            return invitations.saveAndFlush(Invitation.create(address, contactName, shopName, demoRequestId,
                    SecureTokens.sha256(token), now.plus(ttl()), by, now));
        });
        log.info("Setup invite {} created by {}", saved.getId(), by);
        return issue(saved, token);
    }

    /** A new link and a fresh 7 days; the previous link stops working. */
    public Issued resend(String by, UUID id) {
        String token = SecureTokens.newToken();
        Invitation saved = tx.execute(status -> {
            Invitation invitation = find(id);
            if (invitation.getStatus() != Invitation.Status.PENDING) {
                throw ApiException.badRequest("invite-closed", "This invite was "
                        + (invitation.getStatus() == Invitation.Status.ACCEPTED ? "already used" : "revoked")
                        + ". Send a new one instead.");
            }
            Instant now = clock.instant();
            invitation.resend(SecureTokens.sha256(token), now.plus(ttl()), now);
            return invitations.saveAndFlush(invitation);
        });
        log.info("Setup invite {} resent by {}", id, by);
        return issue(saved, token);
    }

    @Transactional
    public Invitation revoke(String by, UUID id) {
        Invitation invitation = find(id);
        if (invitation.getStatus() != Invitation.Status.PENDING) {
            throw ApiException.badRequest("invite-closed", "Only invites not used yet can be revoked.");
        }
        invitation.revoke(by, clock.instant());
        log.info("Setup invite {} revoked by {}", id, by);
        return invitations.saveAndFlush(invitation);
    }

    /** The invite behind a setup link, if it still works (otherwise a 400 saying why). */
    @Transactional(readOnly = true)
    public Invitation check(String token) {
        return usable(invitations.findByTokenHash(hash(token)).orElse(null));
    }

    /**
     * Uses the link in the sign-up's transaction: locks the invite (two tabs can't both create a
     * workshop with it), checks it still works, and marks it used along with its demo request.
     * Call {@link #lock} at the start of the sign-up and this once the workshop exists.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invitation lock(String token) {
        return usable(invitations.lockByTokenHash(hash(token)).orElse(null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(Invitation invitation, UUID tenantId, String slug, String adminUsername) {
        Instant now = clock.instant();
        invitation.accept(tenantId, slug, now);
        invitations.save(invitation);
        if (invitation.getDemoRequestId() != null) {
            demos.findById(invitation.getDemoRequestId()).ifPresent(demo -> demo.onboarded(adminUsername, now));
        }
        log.info("Setup invite {} used for workshop '{}'", invitation.getId(), slug);
    }

    private Invitation usable(Invitation invitation) {
        Instant now = clock.instant();
        Invitation.State state = invitation == null ? Invitation.State.REVOKED : invitation.state(now);
        return switch (state) {
            case PENDING -> invitation;
            case EXPIRED -> throw ApiException.badRequest("invite-expired",
                    "This setup link has expired. Ask Ramplight to send you a new one.");
            case ACCEPTED -> throw ApiException.badRequest("invite-used",
                    "This setup link was already used. Sign in at your workshop's address.");
            case REVOKED -> throw ApiException.badRequest("invite-invalid",
                    "This setup link isn't valid. Ask Ramplight to send you a new one.");
        };
    }

    /**
     * One reminder to each owner whose link is still unused and expires within
     * {@code bayflow.auth.signup.invite-reminder-before} (2 days). Only a hash of the link is kept,
     * so the reminder points to the first e-mail (and Ramplight's team, who can resend). Each
     * invite is marked first, in its own transaction, so it is never reminded twice.
     */
    public int sendReminders() {
        if (!gateway.isAvailable()) {
            return 0;
        }
        Instant now = clock.instant();
        List<Invitation> due = tx.execute(status ->
                invitations.dueReminders(now, now.plus(properties.getSignup().getInviteReminderBefore())));
        int sent = 0;
        for (Invitation candidate : due == null ? List.<Invitation>of() : due) {
            try {
                Invitation marked = tx.execute(status -> {
                    Invitation fresh = invitations.findById(candidate.getId()).orElse(null);
                    if (fresh == null || fresh.getRemindedAt() != null
                            || fresh.state(now) != Invitation.State.PENDING) {
                        return null;
                    }
                    fresh.reminded(now);
                    return invitations.saveAndFlush(fresh);
                });
                if (marked != null) {
                    String replyTo = properties.getDemo().getNotifyEmail();
                    TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> mail.enqueue(MailTemplates.inviteReminder(
                            marked.getEmail(), marked.getContactName(), marked.getShopName(), marked.getLastSentAt(),
                            marked.getExpiresAt(), replyTo.isBlank() ? null : replyTo.strip())));
                    sent++;
                }
            } catch (RuntimeException e) {
                // Another instance got there first, or the outbox refused: the next run tries again.
                log.warn("Setup invite reminder for {} not sent: {}", candidate.getId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} setup invite reminder(s)", sent);
        }
        return sent;
    }

    private Issued issue(Invitation invitation, String token) {
        String url = setupUrl(token);
        boolean emailed = false;
        if (gateway.isAvailable()) {
            TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> mail.enqueue(MailTemplates.invitation(
                    invitation.getEmail(), invitation.getContactName(), invitation.getShopName(), url,
                    ttl().toDays())));
            emailed = true;
        }
        return new Issued(invitation, url, emailed);
    }

    private Invitation find(UUID id) {
        return invitations.findById(id).orElseThrow(() -> ApiException.notFound("Invite"));
    }

    private java.time.Duration ttl() {
        return properties.getSignup().getInviteTtl();
    }

    private String setupUrl(String token) {
        String site = properties.getMail().getSiteUrl().replaceAll("/+$", "");
        return site + SETUP_PATH + "?invite=" + token;
    }

    private static String hash(String token) {
        return SecureTokens.sha256(token == null ? "" : token.strip());
    }
}
