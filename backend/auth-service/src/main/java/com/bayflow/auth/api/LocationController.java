package com.bayflow.auth.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.LocationService;
import com.bayflow.auth.application.LocationService.LocationView;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The organization's locations (shops), with the settings that apply to each. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Locations")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    /**
     * A location. {@code timeZone} and {@code labourRate} are the effective values;
     * {@code ownTimeZone} and {@code ownLabourRate} are its overrides (null = organization's).
     */
    public record LocationResponse(UUID id, String code, String name, String phone, String email, String address,
            String status, String timeZone, BigDecimal labourRate, String currency, String ownTimeZone,
            BigDecimal ownLabourRate, long version) {

        static LocationResponse from(LocationView view) {
            var l = view.location();
            return new LocationResponse(l.getId(), l.getCode(), l.getName(), l.getPhone(), l.getEmail(),
                    l.getAddress(), l.getStatus().name(), view.timeZone(), view.labourRate(), view.currency(),
                    l.getTimeZone(), l.getLabourRate(), l.getVersion() == null ? 0 : l.getVersion());
        }
    }

    /** What other services need to know about a location. */
    public record LocationSettingsResponse(UUID id, String code, String name, boolean active, String timeZone,
            BigDecimal labourRate, String currency) {

        static LocationSettingsResponse from(LocationView view) {
            var l = view.location();
            return new LocationSettingsResponse(l.getId(), l.getCode(), l.getName(), l.isActive(), view.timeZone(),
                    view.labourRate(), view.currency());
        }
    }

    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "The organization's locations, with their effective time zone and labour rate")
    public List<LocationResponse> list() {
        return locations.all().stream().map(LocationResponse::from).toList();
    }

    /** For appointment-service and workshop-service (SYSTEM tokens carry no organization). */
    @GetMapping("/internal/tenants/{tenantId}/locations")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "An organization's locations and their effective settings (SYSTEM)")
    public List<LocationSettingsResponse> forService(@PathVariable UUID tenantId) {
        return TenantContext.call(tenantId, () -> locations.all().stream().map(LocationSettingsResponse::from).toList());
    }

    @GetMapping("/internal/tenants/{tenantId}/locations/{locationId}/settings")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "One location's effective settings (SYSTEM); 404 if it is not the organization's")
    public LocationSettingsResponse settingsForService(@PathVariable UUID tenantId, @PathVariable UUID locationId) {
        return TenantContext.call(tenantId, () -> LocationSettingsResponse.from(locations.get(locationId)));
    }
}
