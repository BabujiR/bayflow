package com.bayflow.appointment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.config.AppointmentProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentAction;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.domain.InvalidTransitionException;
import com.bayflow.appointment.domain.ServiceType;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.infrastructure.AppointmentFilter;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.VehicleRepository;
import com.bayflow.security.CurrentUser;

/**
 * Booking and the appointment workflow up to check-in. Role checks happen on the API;
 * this class enforces ownership (customers only touch their own bookings) and the
 * double-booking rule.
 */
@Service
public class AppointmentService {

    /** Optional filters from the list endpoint. */
    public record ListQuery(LocalDate date, AppointmentStatus status, UUID vehicleId, UUID customerId) {
    }

    private final AppointmentRepository appointments;
    private final VehicleRepository vehicles;
    private final Callers callers;
    private final AppointmentProperties properties;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, VehicleRepository vehicles, Callers callers,
            AppointmentProperties properties, Clock clock) {
        this.appointments = appointments;
        this.vehicles = vehicles;
        this.callers = callers;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(CurrentUser user, UUID vehicleId, Instant scheduledAt, Integer durationMinutes,
            ServiceType serviceType, String notes) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("Vehicle"));
        Customer customer;
        if (Callers.seesAll(user)) {
            customer = vehicle.getOwner();
        } else {
            customer = callers.requireOwnProfile(user);
            if (!vehicle.getOwner().getId().equals(customer.getId())) {
                throw ApiException.notFound("Vehicle");
            }
        }
        int minutes = durationMinutes != null ? durationMinutes : properties.getDefaultDurationMinutes();
        Instant now = clock.instant();
        requireFuture(scheduledAt, now);
        requireFreeSlot(vehicle.getId(), UUID.randomUUID(), scheduledAt, minutes);
        Appointment appointment = Appointment.book(customer, vehicle, scheduledAt, minutes, serviceType, notes, now);
        return appointments.saveAndFlush(appointment);
    }

    /**
     * Advisors and admins see all appointments (with optional filters); customers only their
     * own; mechanics only today's checked-in vehicles.
     */
    @Transactional(readOnly = true)
    public Page<Appointment> list(CurrentUser user, ListQuery query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        ZoneId zone = properties.getWorkshopZone();
        if (Callers.seesAll(user)) {
            return appointments.search(filter(query.customerId(), query, zone), pageable);
        }
        if (user.isMechanic()) {
            LocalDate today = LocalDate.now(clock.withZone(zone));
            return appointments.search(new AppointmentFilter(null, null, AppointmentStatus.CHECKED_IN,
                    startOf(today, zone), startOf(today.plusDays(1), zone)), pageable);
        }
        Optional<Customer> own = callers.findOwnProfile(user);
        if (own.isEmpty()) {
            return Page.empty(pageable);
        }
        return appointments.search(filter(own.get().getId(), query, zone), pageable);
    }

    @Transactional(readOnly = true)
    public Appointment get(CurrentUser user, UUID id) {
        Appointment appointment = find(id);
        boolean visible = Callers.seesAll(user)
                || Callers.owns(user, appointment.getCustomer())
                || (user.isMechanic() && appointment.getStatus() == AppointmentStatus.CHECKED_IN);
        if (!visible) {
            throw ApiException.notFound("Appointment");
        }
        return appointment;
    }

    @Transactional
    public Appointment confirm(UUID id) {
        Appointment appointment = find(id);
        appointment.confirm(clock.instant());
        return appointment;
    }

    @Transactional
    public Appointment arrive(UUID id) {
        Appointment appointment = find(id);
        appointment.arrive(clock.instant());
        return appointment;
    }

    @Transactional
    public Appointment checkIn(UUID id, int mileage) {
        Appointment appointment = find(id);
        int recorded = appointment.getVehicle().getMileage();
        if (mileage < recorded) {
            throw ApiException.badRequest("mileage-decreased", "Mileage at check-in (" + mileage
                    + ") is lower than the last recorded reading (" + recorded + ").");
        }
        appointment.checkIn(mileage, clock.instant());
        return appointment;
    }

    @Transactional
    public Appointment markNoShow(UUID id) {
        Appointment appointment = find(id);
        appointment.markNoShow(clock.instant());
        return appointment;
    }

    @Transactional
    public Appointment cancel(CurrentUser user, UUID id) {
        Appointment appointment = get(user, id);
        appointment.cancel(clock.instant());
        return appointment;
    }

    @Transactional
    public Appointment reschedule(CurrentUser user, UUID id, Instant scheduledAt, Integer durationMinutes) {
        Appointment appointment = get(user, id);
        int minutes = durationMinutes != null ? durationMinutes : appointment.getDurationMinutes();
        Instant now = clock.instant();
        // State first, so rescheduling a closed booking reports invalid-transition rather than an overlap.
        if (!AppointmentAction.RESCHEDULE.isAllowedFrom(appointment.getStatus())) {
            throw new InvalidTransitionException("appointment", appointment.getStatus(),
                    AppointmentAction.RESCHEDULE.description());
        }
        requireFuture(scheduledAt, now);
        requireFreeSlot(appointment.getVehicle().getId(), appointment.getId(), scheduledAt, minutes);
        appointment.reschedule(scheduledAt, minutes, now);
        return appointments.saveAndFlush(appointment);
    }

    private Appointment find(UUID id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    private void requireFuture(Instant scheduledAt, Instant now) {
        if (!scheduledAt.isAfter(now)) {
            throw ApiException.badRequest("scheduled-in-past", "The appointment time must be in the future.");
        }
    }

    private void requireFreeSlot(UUID vehicleId, UUID excludeId, Instant start, int minutes) {
        Instant end = start.plus(Duration.ofMinutes(minutes));
        if (appointments.existsOverlap(vehicleId, excludeId, AppointmentStatus.ACTIVE, start, end)) {
            throw ApiException.conflict("double-booking",
                    "This vehicle already has an appointment that overlaps the requested time.");
        }
    }

    private static AppointmentFilter filter(UUID customerId, ListQuery query, ZoneId zone) {
        Instant from = query.date() == null ? null : startOf(query.date(), zone);
        Instant to = query.date() == null ? null : startOf(query.date().plusDays(1), zone);
        return new AppointmentFilter(customerId, query.vehicleId(), query.status(), from, to);
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }
}
