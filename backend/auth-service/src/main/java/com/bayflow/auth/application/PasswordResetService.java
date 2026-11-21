package com.bayflow.auth.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.AuthService.Session;
import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.PasswordReset;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.domain.TenantSettings;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.PasswordResetRepository;
import com.bayflow.auth.infrastructure.RefreshTokenRepository;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.auth.infrastructure.UserRepository;
import com.bayflow.auth.mail.MailGateway;
import com.bayflow.auth.mail.MailOutbox;
import com.bayflow.auth.mail.MailTemplates;
import com.bayflow.security.TenantContext;

/**
 * "Forgot password?" by e-mail: a single-use, short-lived link to the workshop's own address,
 * then a new password set through it. Answers never reveal whether an e-mail has an account.
 */
@Service
public class PasswordResetService {

    /** The web app's page the link opens. */
    public static final String RESET_PATH = "/reset-password";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final RefreshTokenRepository refreshTokens;
    private final TenantRepository tenants;
    private final TenantSettingsService settings;
    private final AuthService authService;
    private final MailOutbox mail;
    private final MailGateway gateway;
    private final HourlyLimiter limiter;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetRepository resets,
            RefreshTokenRepository refreshTokens, TenantRepository tenants, TenantSettingsService settings,
            AuthService authService, MailOutbox mail, MailGateway gateway, HourlyLimiter limiter,
            PasswordEncoder passwordEncoder, AuthProperties properties, Clock clock) {
        this.users = users;
        this.resets = resets;
        this.refreshTokens = refreshTokens;
        this.tenants = tenants;
        this.settings = settings;
        this.authService = authService;
        this.mail = mail;
        this.gateway = gateway;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Whether this installation sends e-mail, i.e. offers "Forgot password?". */
    public boolean isAvailable() {
        return gateway.isAvailable();
    }

    /**
     * E-mails a reset link to the account with this address, if there is one (and it is enabled).
     * Returns the same way either way.
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        if (!gateway.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "password-reset-unavailable",
                    "Service Unavailable",
                    "Password reset by e-mail is not set up. Ask the workshop to reset your password.");
        }
        AuthProperties.Mail settings = properties.getMail();
        String tenant = String.valueOf(TenantContext.require());
        limiter.check("forgot-ip|" + tenant + "|" + clientIp, settings.getMaxPerIpPerHour());
        String normalized = User.normalizeEmail(email);
        // Per address whether or not it has an account, so the limit itself reveals nothing.
        if (!limiter.tryAcquire("forgot-email|" + tenant + "|" + normalized, settings.getMaxPerEmailPerHour())) {
            return;
        }
        Optional<User> found = users.findByEmail(normalized).filter(User::isEnabled);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        resets.deleteAllForUser(user.getId());
        String token = SecureTokens.newToken();
        Instant now = clock.instant();
        resets.save(PasswordReset.create(user.getId(), SecureTokens.sha256(token), now.plus(settings.getResetLinkTtl()),
                now));
        Workshop workshop = workshop();
        mail.enqueue(MailTemplates.resetLink(user.getEmail(), workshop.name(), workshop.replyTo(),
                user.getUsername(), link(workshop.slug(), token), settings.getResetLinkTtl().toMinutes()));
        log.info("Password reset link e-mailed for account {}", user.getId());
    }

    /**
     * Sets the new password through a link from {@link #requestReset}: the link stops working,
     * the account is unlocked, every session ends and a new one starts here.
     */
    @Transactional
    public Session resetPassword(String token, String newPassword, String clientIp) {
        String tenant = String.valueOf(TenantContext.require());
        // Generous for people, far too few to guess a 256-bit token.
        limiter.check("reset-ip|" + tenant + "|" + clientIp, properties.getMail().getMaxPerIpPerHour() * 3);
        Instant now = clock.instant();
        PasswordReset reset = resets.findByTokenHash(SecureTokens.sha256(token == null ? "" : token))
                .filter(r -> r.isUsable(now))
                .orElseThrow(PasswordResetService::invalidLink);
        User user = users.findById(reset.getUserId()).filter(User::isEnabled)
                .orElseThrow(PasswordResetService::invalidLink);
        user.completeReset(passwordEncoder.encode(newPassword), now);
        users.saveAndFlush(user);
        resets.deleteAllForUser(user.getId());
        refreshTokens.revokeAllForUser(user.getId());
        Workshop workshop = workshop();
        mail.enqueue(MailTemplates.passwordChanged(user.getEmail(), workshop.name(), workshop.replyTo(),
                user.getUsername()));
        log.info("Password of account {} reset through an e-mailed link", user.getId());
        return authService.sessionFor(user);
    }

    private record Workshop(String slug, String name, String replyTo) {
    }

    private Workshop workshop() {
        Tenant tenant = tenants.findById(TenantContext.require())
                .orElseThrow(() -> ApiException.notFound("Workshop"));
        TenantSettings own = settings.current().settings();
        String name = StringUtils.hasText(own.getDisplayName()) ? own.getDisplayName() : tenant.getName();
        return new Workshop(tenant.getSlug(), name, StringUtils.hasText(own.getEmail()) ? own.getEmail() : null);
    }

    /** https://<slug>.<site host>[:port]/reset-password?token=… */
    private String link(String slug, String token) {
        URI site = URI.create(properties.getMail().getSiteUrl());
        String port = site.getPort() == -1 ? "" : ":" + site.getPort();
        return site.getScheme() + "://" + slug + "." + site.getHost() + port + RESET_PATH + "?token=" + token;
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("reset-link-invalid",
                "This reset link has expired or was already used. Ask for a new one.");
    }
}
