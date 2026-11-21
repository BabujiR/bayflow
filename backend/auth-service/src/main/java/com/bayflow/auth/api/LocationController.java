package com.bayflow.auth.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.LocationService;
import com.bayflow.auth.application.LocationService.LocationDetails;
import com.bayflow.auth.application.LocationService.LocationView;
import com.bayflow.auth.application.UserLocationService;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.CurrentUser;
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

    /** What an admin sets; empty timeZone / labourRate = use the organization's. */
    public record LocationRequest(
            @NotBlank @Size(min = 2, max = 20) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address,
            @Size(max = 64) String timeZone,
            @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal labourRate,
            /** Required for updates: the version read. */
            Long version) {

        LocationDetails details() {
            return new LocationDetails(code, name, phone, email, address, timeZone, labourRate);
        }
    }

    private final LocationService locations;
    private final UserLocationService userLocations;

    public LocationController(LocationService locations, UserLocationService userLocations) {
        this.locations = locations;
        this.userLocations = userLocations;
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "The organization's locations, with their effective time zone and labour rate",
            description = "Customers see the active locations only.")
    public List<LocationResponse> list(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        boolean staff = user.isAdmin() || user.isServiceAdvisor() || user.isMechanic();
        return locations.all().stream()
                .filter(view -> staff || view.location().isActive())
                .map(LocationResponse::from)
                .toList();
    }

    @PostMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a location (ADMIN)")
    public LocationResponse create(@Valid @RequestBody LocationRequest request) {
        return LocationResponse.from(locations.create(request.details()));
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a location (ADMIN); send the version you read (409 if stale)")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version-required", "Send the version of the location you read.");
        }
        return LocationResponse.from(locations.update(id, request.details(), request.version()));
    }

    @PostMapping("/locations/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a location (ADMIN): keeps its history, takes no new bookings or staff",
            description = "409 for the last active location or one with upcoming appointments.")
    public LocationResponse archive(@PathVariable UUID id) {
        return LocationResponse.from(locations.archive(id));
    }

    @PostMapping("/locations/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Make an archived location active again (ADMIN)")
    public LocationResponse reactivate(@PathVariable UUID id) {
        return LocationResponse.from(locations.reactivate(id));
    }

    /** For appointment-service and workshop-service (SYSTEM tokens carry no organization). */
    @GetMapping("/internal/tenants/{tenantId}/locations")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "An organization's locations and their effective settings (SYSTEM)")
    public List<LocationSettingsResponse> forService(@PathVariable UUID tenantId) {
        return TenantContext.call(tenantId, () -> locations.all().stream().map(LocationSettingsResponse::from).toList());
    }

    /**
     * For notification-service: who to tell about something at a location. Only roles that work
     * at locations (MANAGER, SERVICE_ADVISOR, MECHANIC); disabled users are left out.
     */
    @GetMapping("/internal/tenants/{tenantId}/locations/{locationId}/staff")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Ids of the enabled staff with the given roles at a location (SYSTEM)")
    public List<UUID> staffForService(@PathVariable UUID tenantId, @PathVariable UUID locationId,
            @RequestParam List<String> roles) {
        java.util.Set<Role> wanted = java.util.EnumSet.noneOf(Role.class);
        for (String name : roles) {
            Role role;
            try {
                role = Role.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("invalid-role", "'" + name + "' is not a role.");
            }
            if (!role.isLocationScoped()) {
                throw ApiException.badRequest("invalid-role", role + " does not work at particular locations.");
            }
            wanted.add(role);
        }
        if (wanted.isEmpty()) {
            throw ApiException.badRequest("invalid-role", "Name at least one role.");
        }
        return TenantContext.call(tenantId, () -> userLocations.staffAt(locationId, wanted));
    }

    @GetMapping("/internal/tenants/{tenantId}/locations/{locationId}/settings")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "One location's effective settings (SYSTEM); 404 if it is not the organization's")
    public LocationSettingsResponse settingsForService(@PathVariable UUID tenantId, @PathVariable UUID locationId) {
        return TenantContext.call(tenantId, () -> LocationSettingsResponse.from(locations.get(locationId)));
    }
}
