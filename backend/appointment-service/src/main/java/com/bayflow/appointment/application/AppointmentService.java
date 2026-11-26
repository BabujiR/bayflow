package com.bayflow.appointment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.bookingservices.ShopChangeMails;
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
import com.bayflow.appointment.infrastructure.AppointmentSort;
import com.bayflow.appointment.infrastructure.VehicleRepository;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.LocationContext;

/**
 * Booking and the appointment workflow up to check-in. Check-in also queues the request
 * that opens the job card in workshop-service (see OutboxService). Role checks happen on the API;
 * this class enforces ownership (customers only touch their own bookings) and the
 * double-booking rule.
 */
@Service
public class AppointmentService {

    /** Optional filters from the list endpoint. */
    /** {@code sort} null = the default: EARLIEST for one day, otherwise UPCOMING. */
    public record ListQuery(LocalDate date, AppointmentStatus status, UUID vehicleId, UUID customerId,
            UUID locationId, AppointmentSort sort) {

        public ListQuery(LocalDate date, AppointmentStatus status, UUID vehicleId, UUID customerId,
                UUID locationId) {
            this(date, status, vehicleId, customerId, locationId, null);
        }
    }

    private final AppointmentRepository appointments;
    private final VehicleRepository vehicles;
    private final Callers callers;
    private final OutboxService outbox;
    private final AppointmentProperties properties;
    private final Locations locations;
    private final ShopChangeMails shopChangeMails;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, VehicleRepository vehicles, Callers callers,
            OutboxService outbox, AppointmentProperties properties, Locations locations,
            ShopChangeMails shopChangeMails, Clock clock) {
        this.appointments = appointments;
        this.vehicles = vehicles;
        this.callers = callers;
        this.outbox = outbox;
        this.properties = properties;
        this.locations = locations;
        this.shopChangeMails = shopChangeMails;
        this.clock = clock;
    }

    /** Event types for notification-service (names as it knows them). */
    static final class Events {
        static final String BOOKED = "APPOINTMENT_BOOKED";
        static final String RESCHEDULED = "APPOINTMENT_RESCHEDULED";
        static final String CANCELLED = "APPOINTMENT_CANCELLED";
        static final String CONFIRMED = "APPOINTMENT_CONFIRMED";
        static final String CHECKED_IN = "VEHICLE_CHECKED_IN";

        private Events() {
        }
    }

    @Transactional
    public Appointment book(CurrentUser user, UUID vehicleId, UUID locationId, Instant scheduledAt,
            Integer durationMinutes, ServiceType serviceType, String notes) {
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
        UUID location = locations.forBooking(locationId);
        requireFreeSlot(vehicle.getId(), UUID.randomUUID(), scheduledAt, minutes);
        Appointment appointment = Appointment.book(customer, vehicle, location, scheduledAt, minutes, serviceType,
                notes, now);
        Appointment saved = appointments.saveAndFlush(appointment);
        outbox.enqueueNotification(saved, Events.BOOKED, null);
        return saved;
    }

    /**
     * Advisors and admins see all appointments (with optional filters); customers only their
     * own; mechanics only today's checked-in vehicles. Days ("date", "today") are those of the
     * location asked for; over several locations, each booking's own location's day. The order is
     * the query's sort; by default a day in time order, anything wider upcoming first.
     */
    @Transactional(readOnly = true)
    public Page<Appointment> list(CurrentUser user, ListQuery query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (Callers.seesAll(user)) {
            return appointments.search(filter(query.customerId(), query), pageable);
        }
        if (user.isMechanic()) {
            Day today = day(zone -> LocalDate.now(clock.withZone(zone)), query.locationId());
            return appointments.search(new AppointmentFilter(null, null, AppointmentStatus.CHECKED_IN,
                    today.from(), today.to(), query.locationId(), today.perLocation(), sortOf(query, true),
                    clock.instant()), pageable);
        }
        Optional<Customer> own = callers.findOwnProfile(user);
        if (own.isEmpty()) {
            return Page.empty(pageable);
        }
        return appointments.search(filter(own.get().getId(), query), pageable);
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
        outbox.enqueueNotification(appointment, Events.CONFIRMED, null);
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
        return checkIn(id, mileage, null, null);
    }

    /**
     * {@code vin} / {@code registration}: needed when the car came from an online booking that
     * didn't give them (the job card needs both); ignored when they are known.
     */
    @Transactional
    public Appointment checkIn(UUID id, int mileage, String vin, String registration) {
        Appointment appointment = find(id);
        if (!appointment.getVehicle().isIdentified()) {
            completeVehicle(appointment.getVehicle(), vin, registration);
        }
        int recorded = appointment.getVehicle().getMileage();
        if (mileage < recorded) {
            throw ApiException.badRequest("mileage-decreased", "Mileage at check-in (" + mileage
                    + ") is lower than the last recorded reading (" + recorded + ").");
        }
        appointment.checkIn(mileage, clock.instant());
        // Saved in this transaction: if the check-in commits, the job-card request will be delivered.
        outbox.enqueueJobCard(appointment);
        outbox.enqueueNotification(appointment, Events.CHECKED_IN, null);
        return appointment;
    }

    /** Re-reads an appointment, e.g. to pick up the job card id after delivery. */
    @Transactional(readOnly = true)
    public Appointment reload(UUID id) {
        return find(id);
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
        outbox.enqueueNotification(appointment, Events.CANCELLED, null);
        if (!user.isCustomer()) {
            shopChangeMails.cancelled(appointment);
        }
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
        Instant previous = appointment.getScheduledAt();
        appointment.reschedule(scheduledAt, minutes, now);
        outbox.enqueueNotification(appointment, Events.RESCHEDULED, previous);
        if (!user.isCustomer()) {
            shopChangeMails.moved(appointment);
        }
        return appointments.saveAndFlush(appointment);
    }

    /**
     * An appointment of a location the caller works at. Row-level security already hides the
     * others; this check makes sure a misconfigured database can't change that (404, as for any
     * appointment the caller may not see).
     */
    private Appointment find(UUID id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
        if (!LocationContext.current().allows(appointment.getLocationId())) {
            throw ApiException.notFound("Appointment");
        }
        return appointment;
    }

    private void completeVehicle(Vehicle vehicle, String vin, String registration) {
        if (vehicle.getVin() == null && (vin == null || !Vehicle.VIN.matcher(Vehicle.normalizeVin(vin)).matches())) {
            throw ApiException.badRequest("vehicle-details-required",
                    "This car was booked online without its VIN: enter the 17-character VIN to check it in.");
        }
        if (vehicle.getRegistration() == null && (registration == null || registration.isBlank())) {
            throw ApiException.badRequest("vehicle-details-required",
                    "This car was booked online without its plate: enter the plate to check it in.");
        }
        if (vehicle.getVin() == null && vehicles.existsByVinAndIdNot(Vehicle.normalizeVin(vin), vehicle.getId())) {
            throw ApiException.conflict("vin-taken", "Another vehicle of this shop already has that VIN.");
        }
        vehicle.complete(vin, registration, clock.instant());
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

    private AppointmentFilter filter(UUID customerId, ListQuery query) {
        Day day = query.date() == null ? Day.ANY : day(zone -> query.date(), query.locationId());
        return new AppointmentFilter(customerId, query.vehicleId(), query.status(), day.from(), day.to(),
                query.locationId(), day.perLocation(), sortOf(query, query.date() != null), clock.instant());
    }

    private static AppointmentSort sortOf(ListQuery query, boolean oneDay) {
        return query.sort() != null ? query.sort() : AppointmentSort.defaultFor(oneDay);
    }

    /** A day as [from, to), or (lists over several locations) one window per location. */
    private record Day(Instant from, Instant to, List<AppointmentFilter.LocationDay> perLocation) {
        static final Day ANY = new Day(null, null, List.of());
    }

    /**
     * The day {@code dayIn} gives in the list's time zone: the location asked for (or the only
     * one the caller works at). A list over several locations uses each location's own day, so
     * an Auckland booking on the 23rd shows on the 23rd, not on New York's 22nd.
     */
    private Day day(Function<ZoneId, LocalDate> dayIn, UUID locationId) {
        boolean oneLocation = locationId != null || LocationContext.current().single().isPresent();
        if (!oneLocation) {
            Map<UUID, ZoneId> zones = locations.zones();
            if (zones.size() > 1) {
                return new Day(null, null, zones.entrySet().stream()
                        .map(e -> {
                            LocalDate date = dayIn.apply(e.getValue());
                            return new AppointmentFilter.LocationDay(e.getKey(), startOf(date, e.getValue()),
                                    startOf(date.plusDays(1), e.getValue()));
                        })
                        .toList());
            }
        }
        ZoneId zone = locations.zoneOf(locations.forList(locationId));
        LocalDate date = dayIn.apply(zone);
        return new Day(startOf(date, zone), startOf(date.plusDays(1), zone), List.of());
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }
}
