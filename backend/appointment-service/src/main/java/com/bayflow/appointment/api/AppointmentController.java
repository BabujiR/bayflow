package com.bayflow.appointment.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.api.AppointmentDtos.AppointmentResponse;
import com.bayflow.appointment.api.AppointmentDtos.BookAppointmentRequest;
import com.bayflow.appointment.api.AppointmentDtos.CheckInRequest;
import com.bayflow.appointment.api.AppointmentDtos.PageResponse;
import com.bayflow.appointment.api.AppointmentDtos.RescheduleRequest;
import com.bayflow.appointment.application.AppointmentService;
import com.bayflow.appointment.application.AppointmentService.ListQuery;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/appointments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Book a service appointment")
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request, Authentication auth) {
        return AppointmentResponse.from(appointments.book(CurrentUser.from(auth), request.vehicleId(),
                request.scheduledAt(), request.durationMinutes(), request.serviceType(), request.notes()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "List appointments",
            description = "Customers see their own; mechanics see today's checked-in vehicles; advisors and "
                    + "admins see all. date is a workshop-local day (YYYY-MM-DD).")
    public PageResponse<AppointmentResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        ListQuery query = new ListQuery(date, status, vehicleId, customerId);
        return PageResponse.from(appointments.list(CurrentUser.from(auth), query, Paging.page(page),
                Paging.size(size)), AppointmentResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "An appointment")
    public AppointmentResponse get(@PathVariable UUID id, Authentication auth) {
        return AppointmentResponse.from(appointments.get(CurrentUser.from(auth), id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "BOOKED → CONFIRMED")
    public AppointmentResponse confirm(@PathVariable UUID id) {
        return AppointmentResponse.from(appointments.confirm(id));
    }

    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "CONFIRMED → ARRIVED (customer is at the desk)")
    public AppointmentResponse arrive(@PathVariable UUID id) {
        return AppointmentResponse.from(appointments.arrive(id));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "ARRIVED → CHECKED_IN, recording the odometer reading")
    public AppointmentResponse checkIn(@PathVariable UUID id, @Valid @RequestBody CheckInRequest request) {
        return AppointmentResponse.from(appointments.checkIn(id, request.mileage()));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "BOOKED/CONFIRMED → NO_SHOW (only after the scheduled time)")
    public AppointmentResponse noShow(@PathVariable UUID id) {
        return AppointmentResponse.from(appointments.markNoShow(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "BOOKED/CONFIRMED → CANCELLED")
    public AppointmentResponse cancel(@PathVariable UUID id, Authentication auth) {
        return AppointmentResponse.from(appointments.cancel(CurrentUser.from(auth), id));
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Move a BOOKED/CONFIRMED appointment to a new time (status unchanged)")
    public AppointmentResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request,
            Authentication auth) {
        return AppointmentResponse.from(appointments.reschedule(CurrentUser.from(auth), id,
                request.scheduledAt(), request.durationMinutes()));
    }
}
