package com.bayflow.appointment.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * For workshop-service (SYSTEM tokens only): who to bill for an appointment's job, copied onto
 * the invoice. Not routed by nginx.
 */
@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/appointments/{appointmentId}")
@Tag(name = "Internal")
public class InternalAppointmentController {

    /** The customer and vehicle of an appointment, as an invoice shows them. */
    public record BillingContact(UUID customerId, String name, String email, String phone, String vehicleMake,
            String vehicleModel, Integer vehicleYear) {

        static BillingContact of(Appointment appointment) {
            var customer = appointment.getCustomer();
            var vehicle = appointment.getVehicle();
            return new BillingContact(customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone(),
                    vehicle.getMake(), vehicle.getModel(), vehicle.getYear() > 0 ? vehicle.getYear() : null);
        }
    }

    private final AppointmentRepository appointments;

    public InternalAppointmentController(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/billing")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "The customer and vehicle to put on the appointment's invoice (SYSTEM)")
    public BillingContact billing(@PathVariable UUID tenantId, @PathVariable UUID appointmentId) {
        return TenantContext.call(tenantId, () -> appointments.findById(appointmentId).map(BillingContact::of))
                .orElseThrow(() -> ApiException.notFound("Appointment"));
    }
}
