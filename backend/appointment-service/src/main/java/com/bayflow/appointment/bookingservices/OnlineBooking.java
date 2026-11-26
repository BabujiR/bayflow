package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.OutboxService;
import com.bayflow.appointment.config.AppointmentProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.domain.ServiceType;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.CustomerRepository;
import com.bayflow.appointment.infrastructure.VehicleRepository;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;

/**
 * A booking made on the public booking page, without signing in. The customer is found by e-mail
 * (or created, without a login), the car by VIN or by year, make and model among theirs (or
 * added), and the time must still be free. Confirmed at once; staff are told as for any booking.
 */
@Service
public class OnlineBooking {

    private static final Logger log = LoggerFactory.getLogger(OnlineBooking.class);

    /** The starting services' codes and the appointment type they stand for (others: OTHER). */
    static final Map<String, ServiceType> TYPES = Map.of(
            "oil-change", ServiceType.OIL_CHANGE,
            "routine-service", ServiceType.ROUTINE_SERVICE,
            "brakes", ServiceType.BRAKES,
            "tires", ServiceType.TYRES,
            "inspection", ServiceType.INSPECTION,
            "diagnostic", ServiceType.DIAGNOSTIC);

    public static final String SOMETHING_ELSE = "Something else";

    /** What the page sends. {@code serviceCode} null: "Something else" (then a description is needed). */
    public record Request(String serviceCode, String description, String vin, Integer year, String make,
            String model, Instant startsAt, String firstName, String lastName, String email, String phone,
            boolean smsConsent) {
    }

    private final BookingServiceRepository services;
    private final BookingHours hours;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final AppointmentRepository appointments;
    private final OutboxService outbox;
    private final AppointmentProperties properties;
    private final Clock clock;

    public OnlineBooking(BookingServiceRepository services, BookingHours hours, CustomerRepository customers,
            VehicleRepository vehicles, AppointmentRepository appointments, OutboxService outbox,
            AppointmentProperties properties, Clock clock) {
        this.services = services;
        this.hours = hours;
        this.customers = customers;
        this.vehicles = vehicles;
        this.appointments = appointments;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(LocationSettings location, ZoneId zone, Request r) {
        checkVehicle(r);
        BookingService service = null;
        if (r.serviceCode() != null && !r.serviceCode().isBlank()) {
            service = services.findAll().stream()
                    .filter(s -> s.getCode().equals(r.serviceCode()) && s.isActive() && s.offeredAt(location.id()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("invalid-service",
                            "That service isn't offered here any more. Choose another."));
        } else if (r.description() == null || r.description().strip().length() < 3) {
            throw ApiException.badRequest("description-required", "Tell us briefly what your car needs.");
        }

        // One booking at a time per location and slot, so the last place can't be taken twice.
        appointments.lockSlot(location.id() + "|" + r.startsAt());
        if (!hours.isFree(location.id(), zone, r.startsAt())) {
            throw ApiException.conflict("slot-taken", "That time was just taken. Choose another.");
        }

        Instant now = clock.instant();
        String email = r.email().strip().toLowerCase(Locale.ROOT);
        String phone = r.phone() == null || r.phone().isBlank() ? null : r.phone().strip();
        Customer customer = customers.findFirstByEmailOrderByCreatedAtAsc(email).orElseGet(() ->
                customers.saveAndFlush(Customer.create(null, r.firstName().strip() + " " + r.lastName().strip(),
                        email, phone, now)));
        Vehicle vehicle = vehicleFor(customer, r, now);

        int minutes = service != null && service.getDurationMinutes() != null
                ? Math.max(Appointment.MIN_DURATION_MINUTES,
                        Math.min(Appointment.MAX_DURATION_MINUTES, service.getDurationMinutes()))
                : properties.getDefaultDurationMinutes();
        if (appointments.existsOverlap(vehicle.getId(), UUID.randomUUID(), AppointmentStatus.ACTIVE,
                r.startsAt(), r.startsAt().plusSeconds(minutes * 60L))) {
            throw ApiException.conflict("double-booking", "This car already has a booking at that time.");
        }
        ServiceType type = service == null ? ServiceType.OTHER
                : TYPES.getOrDefault(service.getCode(), ServiceType.OTHER);
        String notes = r.description() == null || r.description().isBlank() ? null : r.description().strip();
        Appointment appointment = Appointment.book(customer, vehicle, location.id(), r.startsAt(), minutes, type,
                notes, now);
        appointment.bookedOnline(service == null ? SOMETHING_ELSE : service.getName(),
                service == null ? null : service.getPriceKind().name(), service == null ? null : service.getPrice(),
                phone != null && r.smsConsent() ? now : null);
        Appointment saved = appointments.saveAndFlush(appointment);
        outbox.enqueueNotification(saved, "APPOINTMENT_BOOKED", null);
        log.info("Online booking {} at location {}", saved.getId(), location.id());
        return saved;
    }

    private void checkVehicle(Request r) {
        boolean hasVin = r.vin() != null && !r.vin().isBlank();
        if (hasVin) {
            if (!Vehicle.VIN.matcher(Vehicle.normalizeVin(r.vin())).matches()) {
                throw ApiException.badRequest("invalid-vin",
                        "A VIN has 17 letters and digits (no I, O or Q). Check it, or use year, make and model.");
            }
            return;
        }
        int nextYear = Year.now(clock).getValue() + 1;
        if (r.year() == null || r.year() < 1950 || r.year() > nextYear || r.make() == null || r.make().isBlank()
                || r.model() == null || r.model().isBlank()) {
            throw ApiException.badRequest("vehicle-required", "Give your car's VIN, or its year, make and model.");
        }
    }

    /** The customer's car this booking is for: by VIN, else by year, make and model; else a new one. */
    private Vehicle vehicleFor(Customer customer, Request r, Instant now) {
        if (r.vin() != null && !r.vin().isBlank()) {
            String vin = Vehicle.normalizeVin(r.vin());
            return vehicles.findByVin(vin)
                    .map(v -> {
                        if (!v.getOwner().getId().equals(customer.getId())) {
                            throw ApiException.conflict("vin-registered", "This VIN is already with the shop"
                                    + " under another customer. Please call the shop.");
                        }
                        return v;
                    })
                    .orElseGet(() -> vehicles.saveAndFlush(
                            Vehicle.fromOnlineBooking(customer, vin, null, null, null, now)));
        }
        return vehicles.findAllByOwnerId(customer.getId()).stream()
                .filter(v -> v.getYear() == r.year() && r.make().strip().equalsIgnoreCase(v.getMake())
                        && r.model().strip().equalsIgnoreCase(v.getModel()))
                .findFirst()
                .orElseGet(() -> vehicles.saveAndFlush(
                        Vehicle.fromOnlineBooking(customer, null, r.make(), r.model(), r.year(), now)));
    }
}
