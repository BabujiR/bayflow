package com.bayflow.auth.api;

import static com.bayflow.auth.api.AuthDtos.USERNAME_MESSAGE;
import static com.bayflow.auth.api.AuthDtos.USERNAME_PATTERN;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.BrandingService;
import com.bayflow.auth.application.BrandingService.BrandingView;
import com.bayflow.auth.application.SignupRateLimiter;
import com.bayflow.auth.application.SignupService;
import com.bayflow.auth.application.SignupService.Availability;
import com.bayflow.auth.application.SignupService.Created;
import com.bayflow.auth.application.TenantSettingsService;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.domain.TenantAsset;
import com.bayflow.auth.domain.TenantBranding;
import com.bayflow.auth.domain.TenantBranding.BannerStyle;
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
    public record PublicWorkshop(String slug, String name, String displayName, boolean active,
            PublicBranding branding) {
    }

    /** The workshop's look, for everyone at its address. The banner is only present while it is live. */
    public record PublicBranding(String logoUrl, String coverUrl, String primaryColor, boolean showPoweredBy,
            PublicBanner banner) {
    }

    public record PublicBanner(String text, BannerStyle style) {
    }

    private final SignupService signups;
    private final SignupRateLimiter rateLimiter;
    private final RequestTenants requestTenants;
    private final TenantSettingsService settings;
    private final BrandingService branding;
    private final Clock clock;

    public WorkshopSignupController(SignupService signups, SignupRateLimiter rateLimiter,
            RequestTenants requestTenants, TenantSettingsService settings, BrandingService branding, Clock clock) {
        this.signups = signups;
        this.rateLimiter = rateLimiter;
        this.requestTenants = requestTenants;
        this.settings = settings;
        this.branding = branding;
        this.clock = clock;
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
        BrandingView view = TenantContext.call(tenant.getId(), branding::current);
        return new PublicWorkshop(tenant.getSlug(), tenant.getName(), displayName, tenant.isActive(),
                publicBranding(view, clock.instant()));
    }

    @GetMapping("/current/{kind:logo|cover}")
    @Operation(summary = "The logo or sign-in cover photo of the workshop at this address (404 when none)",
            description = "Cached by the browser: forever with the ?v= of /current, otherwise revalidated "
                    + "with the ETag.")
    public ResponseEntity<byte[]> image(@PathVariable String kind, @RequestParam(required = false) String v,
            HttpServletRequest httpRequest) {
        Tenant tenant = requestTenants.workshopOf(httpRequest);
        TenantAsset asset = TenantContext.call(tenant.getId(), () -> branding.image(BrandingController.kindOf(kind)))
                .orElseThrow(() -> ApiException.notFound("The image"));
        String etag = "\"" + asset.getSha256() + "\"";
        CacheControl cache = BrandingController.version(asset.getSha256()).equals(v)
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache().cachePublic();
        if (etag.equals(httpRequest.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .eTag(etag)
                .cacheControl(cache)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(asset.getContent());
    }

    static PublicBranding publicBranding(BrandingView view, Instant now) {
        TenantBranding b = view.branding();
        PublicBanner banner = b.isBannerLive(now) ? new PublicBanner(b.getBannerText(), b.getBannerStyle()) : null;
        return new PublicBranding(BrandingController.imageUrl(TenantAsset.Kind.LOGO, view.logoSha256()),
                BrandingController.imageUrl(TenantAsset.Kind.COVER, view.coverSha256()), b.getPrimaryColor(),
                b.isShowPoweredBy(), banner);
    }
}
