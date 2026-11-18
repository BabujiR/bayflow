package com.bayflow.auth.api;

import static com.bayflow.auth.api.AuthDtos.USERNAME_MESSAGE;
import static com.bayflow.auth.api.AuthDtos.USERNAME_PATTERN;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.SignupRateLimiter;
import com.bayflow.auth.application.SignupService;
import com.bayflow.auth.application.SignupService.Availability;
import com.bayflow.auth.application.SignupService.Created;
import com.bayflow.auth.application.TenantSettingsService;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public workshop endpoints: sign up a new workshop, check an address, and name the current one. */
@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Workshops")
public class WorkshopSignupController {

    public record SignupRequest(
            @NotBlank @Size(max = 100) String workshopName,
            @NotBlank @Size(min = 3, max = 30) String slug,
            @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal labourRate,
            @Size(max = 3) String currency,
            @Size(max = 64) String timeZone,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String adminUsername,
            @NotBlank @Email @Size(max = 254) String adminEmail,
            @NotBlank @Size(min = 8, max = 100) String adminPassword) {
    }

    public record SignupResponse(UUID id, String slug, String name, String adminUsername) {
    }

    public record AvailabilityResponse(String slug, boolean available, String reason) {
    }

    /** What the sign-in page of a workshop's address shows. */
    public record PublicWorkshop(String slug, String name, String displayName, boolean active) {
    }

    private final SignupService signups;
    private final SignupRateLimiter rateLimiter;
    private final RequestTenants requestTenants;
    private final TenantSettingsService settings;

    public WorkshopSignupController(SignupService signups, SignupRateLimiter rateLimiter,
            RequestTenants requestTenants, TenantSettingsService settings) {
        this.signups = signups;
        this.rateLimiter = rateLimiter;
        this.requestTenants = requestTenants;
        this.settings = settings;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sign up a new workshop with its first ADMIN (public, rate-limited per IP)",
            description = "Send the X-Bayflow-Client header like the web app. The workshop is then at "
                    + "<slug>.<base domain>; sign in there.")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        CookieRequestGuard.check(httpRequest);
        rateLimiter.check(httpRequest.getRemoteAddr());
        UUID id = UUID.randomUUID();
        Created created = TenantContext.call(id, () -> signups.signUp(id, new SignupService.Signup(
                request.workshopName(), request.slug(), request.labourRate(), request.currency(), request.timeZone(),
                request.adminUsername(), request.adminEmail(), request.adminPassword())));
        return new SignupResponse(created.tenant().getId(), created.tenant().getSlug(), created.tenant().getName(),
                created.admin().getUsername());
    }

    @GetMapping("/slug-availability")
    @Operation(summary = "Whether an address is free for a new workshop")
    public AvailabilityResponse availability(@RequestParam String slug) {
        String normalized = SignupService.normalize(slug);
        Availability availability = signups.availability(normalized);
        String reason = switch (availability) {
            case AVAILABLE -> null;
            case TAKEN -> "taken";
            case RESERVED -> "reserved";
            case INVALID -> "invalid";
        };
        return new AvailabilityResponse(normalized, availability == Availability.AVAILABLE, reason);
    }

    @GetMapping("/current")
    @Operation(summary = "The workshop at this address, for its sign-in page (404 when there is none)")
    public PublicWorkshop current(HttpServletRequest httpRequest) {
        Tenant tenant = requestTenants.workshopOf(httpRequest);
        String displayName = TenantContext.call(tenant.getId(), () -> settings.current().settings().getDisplayName());
        return new PublicWorkshop(tenant.getSlug(), tenant.getName(), displayName, tenant.isActive());
    }
}
