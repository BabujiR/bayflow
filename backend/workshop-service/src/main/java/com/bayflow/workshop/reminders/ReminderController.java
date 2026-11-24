package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.Authentication;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.reminders.ServiceDueService.DueService;
import com.bayflow.workshop.reminders.ServiceDueService.DueVehicle;
import com.bayflow.workshop.reminders.ServiceInterval.Details;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Maintenance reminders: admins keep the service intervals and settings; staff see which
 * vehicles are due (the Due soon list, and at check-in on the job card).
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Service reminders")
public class ReminderController {

    /** miles and/or months (at least one); version required to update. */
    public record IntervalRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(100) @Max(200000) Integer miles,
            @Min(1) @Max(120) Integer months,
            @Size(max = 20) List<@Size(max = 60) String> keywords,
            @Size(max = 50) List<UUID> cannedJobIds,
            Boolean active,
            Long version) {

        Details details() {
            if (miles == null && months == null) {
                throw ApiException.badRequest("interval-empty", "Give the miles, the months or both.");
            }
            return new Details(name, miles, months, keywords, cannedJobIds, active == null || active);
        }
    }

    public record IntervalResponse(UUID id, String name, Integer miles, Integer months, List<String> keywords,
            List<UUID> cannedJobIds, boolean active, long version) {

        static IntervalResponse from(ServiceInterval i) {
            return new IntervalResponse(i.getId(), i.getName(), i.getMiles(), i.getMonths(), i.getKeywords(),
                    i.getCannedJobIds(), i.isActive(), i.getVersion() == null ? 0 : i.getVersion());
        }
    }

    public record SettingsRequest(@NotNull Boolean enabled, @NotNull @Min(0) @Max(90) Integer remindDaysBefore,
            @NotNull @Min(1) @Max(500) Integer milesPerDay, @NotNull Long version) {
    }

    public record SettingsResponse(boolean enabled, int remindDaysBefore, int milesPerDay, long version) {

        static SettingsResponse from(ReminderSettings s) {
            return new SettingsResponse(s.isEnabled(), s.getRemindDaysBefore(), s.getMilesPerDay(),
                    s.getVersion() == null ? 0 : s.getVersion());
        }
    }

    /** Whether the signed-in customer gets service reminders. */
    public record MyReminders(@NotNull Boolean enabled) {
    }

    private final ServiceIntervalService intervals;
    private final ServiceDueService due;
    private final ReminderService reminders;

    public ReminderController(ServiceIntervalService intervals, ServiceDueService due, ReminderService reminders) {
        this.intervals = intervals;
        this.due = due;
        this.reminders = reminders;
    }

    @GetMapping("/service-intervals")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "The shop's service intervals (a starter list the first time)")
    public List<IntervalResponse> intervals() {
        return intervals.list().stream().map(IntervalResponse::from).toList();
    }

    @PostMapping("/service-intervals")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a service interval (ADMIN)")
    public IntervalResponse create(@Valid @RequestBody IntervalRequest request) {
        return IntervalResponse.from(intervals.create(request.details()));
    }

    @PutMapping("/service-intervals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a service interval (ADMIN; send the version read)")
    public IntervalResponse update(@PathVariable UUID id, @Valid @RequestBody IntervalRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version-required", "Send the version you read.");
        }
        return IntervalResponse.from(intervals.update(id, request.version(), request.details()));
    }

    @DeleteMapping("/service-intervals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a service interval and what was recorded for it (ADMIN)")
    public void delete(@PathVariable UUID id) {
        intervals.delete(id);
    }

    @GetMapping("/service-reminders/settings")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Reminders on or off, how many days before, and the miles a day assumed")
    public SettingsResponse settings() {
        return SettingsResponse.from(intervals.settings());
    }

    @PutMapping("/service-reminders/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change the reminder settings (ADMIN)")
    public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.from(intervals.updateSettings(request.version(), request.enabled(),
                request.remindDaysBefore(), request.milesPerDay()));
    }

    @GetMapping("/service-due")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Vehicles with a service due within `days` (or overdue), soonest first",
            description = "Only vehicles last serviced at the caller's locations; locationId narrows to one.")
    public List<DueVehicle> dueSoon(@RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) UUID locationId) {
        return due.dueSoon(Math.min(Math.max(days, 0), 365), locationId);
    }

    @PostMapping("/service-due/vehicles/{vehicleId}/remind")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MANAGER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Send the customer a reminder now for the vehicle's services due within 60 days",
            description = "409 when the customer has no account or turned reminders off.")
    public void remind(@PathVariable UUID vehicleId) {
        reminders.remindNow(vehicleId);
    }

    @GetMapping("/service-reminders/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Whether the signed-in customer gets service reminders")
    public MyReminders mine(Authentication auth) {
        return new MyReminders(reminders.isEnabledFor(customer(auth)));
    }

    @PutMapping("/service-reminders/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Turn service reminders on or off for the signed-in customer")
    public MyReminders setMine(@Valid @RequestBody MyReminders request, Authentication auth) {
        reminders.setEnabledFor(customer(auth), request.enabled());
        return new MyReminders(request.enabled());
    }

    private static UUID customer(Authentication auth) {
        try {
            return UUID.fromString(CurrentUser.from(auth).subject());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Customer");
        }
    }

    @GetMapping("/service-due/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN')")
    @Operation(summary = "A vehicle's services due within 30 days or 500 miles of `mileage` (at check-in)")
    public List<DueService> forVehicle(@PathVariable UUID vehicleId, @RequestParam(required = false) Integer mileage) {
        return due.forVehicle(vehicleId, mileage);
    }
}
