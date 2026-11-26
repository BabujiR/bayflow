package com.bayflow.auth.api;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.TenantSettingsService;
import com.bayflow.auth.application.TenantSettingsService.SettingsUpdate;
import com.bayflow.auth.application.TenantSettingsService.WorkshopView;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The signed-in user's workshop and its settings. Under /api/v1/auth so nginx routes it to
 * auth-service like the rest of the account API.
 */
@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Workshop settings")
public class TenantController {

    public record WorkshopResponse(UUID id, String slug, String name, String displayName, String phone,
            String email, String address, BigDecimal labourRate, String currency, String timeZone, long version) {

        static WorkshopResponse from(WorkshopView view) {
            var s = view.settings();
            return new WorkshopResponse(view.tenant().getId(), view.tenant().getSlug(), view.tenant().getName(),
                    s.getDisplayName(), s.getPhone(), s.getEmail(), s.getAddress(), s.getLabourRate(),
                    s.getCurrency(), s.getTimeZone(), s.getVersion() == null ? 0 : s.getVersion());
        }
    }

    public record UpdateSettingsRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address,
            @NotNull @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal labourRate,
            @NotBlank @Size(max = 3) String currency,
            @NotBlank @Size(max = 64) String timeZone,
            @NotNull Long version) {
    }

    /** A workshop found by its address, for a public page served by another service. */
    public record WorkshopByAddress(UUID id, String slug, String name, String displayName, boolean active) {
    }

    private final TenantSettingsService settings;
    private final TenantRepository tenants;

    public TenantController(TenantSettingsService settings, TenantRepository tenants) {
        this.settings = settings;
        this.tenants = tenants;
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "The signed-in user's workshop and its settings")
    public WorkshopResponse current() {
        return WorkshopResponse.from(settings.current());
    }

    @PutMapping("/tenant/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change the workshop's settings (workshop ADMIN)",
            description = "Send the version you read; 409 if someone changed the settings meanwhile.")
    public WorkshopResponse update(@Valid @RequestBody UpdateSettingsRequest request) {
        return WorkshopResponse.from(settings.update(new SettingsUpdate(request.displayName(), request.phone(),
                request.email(), request.address(), request.labourRate(), request.currency(), request.timeZone(),
                request.version())));
    }

    /** For appointment-service and workshop-service, which hold SYSTEM tokens without a workshop. */
    @GetMapping("/internal/tenants/{tenantId}/settings")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "A workshop's settings, for other Bayflow services (SYSTEM)")
    public WorkshopResponse forService(@PathVariable UUID tenantId) {
        return TenantContext.call(tenantId, () -> WorkshopResponse.from(settings.current()));
    }

    /**
     * For appointment-service's public booking page (<slug>.<base domain>/book): which workshop an
     * address names. 404 for unknown addresses and for the Ramplight console's organization.
     */
    @GetMapping("/internal/workshops/{slug}")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "The workshop at an address (SYSTEM)")
    public WorkshopByAddress byAddress(@PathVariable String slug) {
        Tenant tenant = tenants.findBySlug(slug.toLowerCase(Locale.ROOT))
                .filter(t -> !Platform.TENANT_ID.equals(t.getId()))
                .orElseThrow(() -> ApiException.notFound("Workshop"));
        String displayName = TenantContext.call(tenant.getId(), () -> settings.current().settings().getDisplayName());
        return new WorkshopByAddress(tenant.getId(), tenant.getSlug(), tenant.getName(), displayName,
                tenant.isActive());
    }
}
