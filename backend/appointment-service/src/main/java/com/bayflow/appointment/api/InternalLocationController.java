package com.bayflow.appointment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * For auth-service (SYSTEM tokens only): before a location is archived, how many upcoming
 * appointments it still has. Not routed by nginx (only /api/v1/customers, vehicles and
 * appointments reach this service from the browser).
 */
@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/locations/{locationId}")
@Tag(name = "Internal")
public class InternalLocationController {

    /** Still to happen: booked, confirmed, or arrived and waiting for check-in. */
    static final List<AppointmentStatus> UPCOMING =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED, AppointmentStatus.ARRIVED);

    public record Upcoming(long count) {
    }

    private final AppointmentRepository appointments;

    public InternalLocationController(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Upcoming appointments at a location (SYSTEM)")
    public Upcoming upcoming(@PathVariable UUID tenantId, @PathVariable UUID locationId) {
        return new Upcoming(TenantContext.call(tenantId,
                () -> appointments.countByLocationIdAndStatusIn(locationId, UPCOMING)));
    }
}
