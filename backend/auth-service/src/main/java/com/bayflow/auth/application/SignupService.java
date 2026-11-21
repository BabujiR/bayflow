package com.bayflow.auth.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.security.TenantContext;

/**
 * Self-service sign-up: a new workshop, its settings, its first location and its owner (an ADMIN),
 * all or nothing.
 * The workshop's address is {@code <slug>.<base domain>}; the slug never changes.
 */
@Service
public class SignupService {

    /** 3-30 characters: a-z, 0-9 and hyphens, starting and ending with a letter or digit. */
    static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$");

    /** Addresses kept for the platform itself, or too confusing to hand out. */
    static final Set<String> RESERVED = Set.of(
            "www", "app", "api", "admin", "administrator", "auth", "login", "signup", "sign-up",
            "register", "account", "accounts", "billing", "status", "mail", "email", "smtp", "imap",
            "help", "support", "docs", "doc", "blog", "news", "static", "assets", "cdn", "media",
            "files", "dev", "test", "staging", "prod", "production", "demo-admin", "localhost",
            "default", "root", "system", "bayflow", "ramplight", "security", "dashboard", "internal", "ns1", "ns2");

    public enum Availability { AVAILABLE, TAKEN, RESERVED, INVALID }

    public record Signup(String workshopName, String slug, BigDecimal labourRate, String currency,
            String timeZone, String adminUsername, String adminEmail, String adminPassword) {
    }

    public record Created(Tenant tenant, User admin) {
    }

    private final TenantRepository tenants;
    private final TenantSettingsService settings;
    private final LocationService locations;
    private final AuthService authService;
    private final Clock clock;

    public SignupService(TenantRepository tenants, TenantSettingsService settings, LocationService locations,
            AuthService authService, Clock clock) {
        this.tenants = tenants;
        this.settings = settings;
        this.locations = locations;
        this.authService = authService;
        this.clock = clock;
    }

    /** Normalizes a requested slug (trim, lower case). */
    public static String normalize(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase();
    }

    @Transactional(readOnly = true)
    public Availability availability(String requested) {
        String slug = normalize(requested);
        if (!SLUG.matcher(slug).matches() || slug.contains("--")) {
            return Availability.INVALID;
        }
        if (RESERVED.contains(slug)) {
            return Availability.RESERVED;
        }
        return tenants.existsBySlug(slug) ? Availability.TAKEN : Availability.AVAILABLE;
    }

    /**
     * Creates the workshop with id {@code tenantId}. Must run inside
     * {@code TenantContext.call(tenantId, ...)}: row-level security only lets the new workshop's
     * settings and admin be written within that workshop.
     */
    @Transactional
    public Created signUp(UUID tenantId, Signup request) {
        if (!tenantId.equals(TenantContext.require())) {
            throw new IllegalStateException("Sign-up must run in the new workshop's context");
        }
        String slug = normalize(request.slug());
        switch (availability(slug)) {
            case INVALID -> throw ApiException.badRequest("invalid-slug", "Use 3-30 lower-case letters, digits"
                    + " and single hyphens, starting and ending with a letter or digit.");
            case RESERVED -> throw ApiException.badRequest("slug-reserved",
                    "'" + slug + "' is reserved. Choose another address.");
            case TAKEN -> throw taken(slug);
            case AVAILABLE -> {
                // go on
            }
        }
        String name = request.workshopName().trim();
        try {
            tenants.saveAndFlush(Tenant.create(tenantId, slug, name, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Someone took the slug between the check and the insert.
            throw taken(slug);
        }
        settings.createForNewWorkshop(name, request.labourRate(), request.currency(), request.timeZone());
        // Its first location, "Main" (id = tenant id), using the organization's settings.
        locations.ensureMain();
        User admin = authService.createUser(request.adminUsername(), request.adminEmail(), request.adminPassword(),
                Set.of(Role.ADMIN));
        return new Created(tenants.findById(tenantId).orElseThrow(), admin);
    }

    private static ApiException taken(String slug) {
        return ApiException.conflict("slug-taken", "'" + slug + "' is already taken. Choose another address.");
    }
}
