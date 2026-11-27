package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.Locations;
import com.bayflow.appointment.application.OutboxService;
import com.bayflow.appointment.bookingservices.BookingConfirmation.Kind;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;

/**
 * The customer's own changes to an online booking, from the private link in its e-mails (no
 * sign-in): see it, move it (at most {@link #MAX_RESCHEDULES} times) or cancel it. Changes stop
 * once the car has arrived, and inside the location's booking notice (default 2 hours): then
 * the customer calls the shop. The shop is told as for any booking the customer changes.
 */
@Service
public class ManageBooking {

    public static final int MAX_RESCHEDULES = 3;

    /** Why the booking can't be changed from the link any more (NONE: it can). */
    public enum Blocked { NONE, CANCELLED, ARRIVED, PAST, TOO_LATE, LOCATION_CLOSED }

    /** {@code location} null: the location was archived. */
    public record View(Appointment appointment, LocationSettings location, ZoneId zone, boolean manyLocations,
            Blocked blocked, int noticeMinutes, int reschedulesLeft) {

        public boolean canChange() {
            return blocked == Blocked.NONE;
        }
    }

    private final AppointmentRepository appointments;
    private final BookingHours hours;
    private final Locations locations;
    private final OutboxService outbox;
    private final BookingConfirmation mails;
    private final Clock clock;

    public ManageBooking(AppointmentRepository appointments, BookingHours hours, Locations locations,
            OutboxService outbox, BookingConfirmation mails, Clock clock) {
        this.appointments = appointments;
        this.hours = hours;
        this.locations = locations;
        this.outbox = outbox;
        this.mails = mails;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View view(String token) {
        return viewOf(find(token));
    }

    @Transactional
    public View reschedule(Workshop shop, String token, Instant startsAt) {
        Appointment a = find(token);
        View view = viewOf(a);
        requireChangeable(view);
        if (view.reschedulesLeft() == 0) {
            throw ApiException.conflict("reschedule-limit", "This booking was already moved " + MAX_RESCHEDULES
                    + " times. Please call the shop to change it.");
        }
        if (startsAt.equals(a.getScheduledAt())) {
            throw ApiException.badRequest("same-time", "You're already booked for that time.");
        }
        appointments.lockSlot(a.getLocationId() + "|" + startsAt);
        if (!hours.isFree(a.getLocationId(), view.zone(), startsAt)) {
            throw ApiException.conflict("slot-taken", "That time was just taken. Choose another.");
        }
        Instant end = startsAt.plus(Duration.ofMinutes(a.getDurationMinutes()));
        if (appointments.existsOverlap(a.getVehicle().getId(), a.getId(), AppointmentStatus.ACTIVE, startsAt, end)) {
            throw ApiException.conflict("double-booking", "This car already has a booking at that time.");
        }
        Instant previous = a.getScheduledAt();
        a.rescheduleByCustomer(startsAt, clock.instant());
        Appointment saved = appointments.saveAndFlush(a);
        outbox.enqueueNotification(saved, OnlineBooking.RESCHEDULED, previous, true);
        mails.send(shop, view.location(), view.manyLocations(), view.zone(), saved, Kind.MOVED_BY_YOU, token);
        return viewOf(saved);
    }

    @Transactional
    public View cancel(Workshop shop, String token, String reason) {
        Appointment a = find(token);
        View view = viewOf(a);
        requireChangeable(view);
        a.cancelByCustomer(reason, clock.instant());
        Appointment saved = appointments.saveAndFlush(a);
        outbox.enqueueNotification(saved, OnlineBooking.CANCELLED, null, true);
        mails.send(shop, view.location(), view.manyLocations(), view.zone(), saved, Kind.CANCELLED_BY_YOU, null);
        return viewOf(saved);
    }

    private View viewOf(Appointment a) {
        List<LocationSettings> active = locations.active();
        LocationSettings location = active.stream().filter(l -> l.id().equals(a.getLocationId())).findFirst()
                .orElse(null);
        ZoneId zone = location != null ? locations.zoneOf(location) : locations.zoneOf(a.getLocationId());
        int notice = hours.settingsFor(a.getLocationId()).getMinNoticeMinutes();
        Instant now = clock.instant();
        Blocked blocked;
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.NO_SHOW) {
            blocked = Blocked.CANCELLED;
        } else if (a.getStatus() == AppointmentStatus.ARRIVED || a.getStatus() == AppointmentStatus.CHECKED_IN) {
            blocked = Blocked.ARRIVED;
        } else if (!a.getScheduledAt().isAfter(now)) {
            blocked = Blocked.PAST;
        } else if (location == null) {
            blocked = Blocked.LOCATION_CLOSED;
        } else if (!a.getScheduledAt().minus(Duration.ofMinutes(notice)).isAfter(now)) {
            blocked = Blocked.TOO_LATE;
        } else {
            blocked = Blocked.NONE;
        }
        return new View(a, location, zone, active.size() > 1, blocked, notice,
                Math.max(0, MAX_RESCHEDULES - a.getCustomerReschedules()));
    }

    private static void requireChangeable(View view) {
        switch (view.blocked()) {
            case NONE -> {
            }
            case CANCELLED -> throw ApiException.conflict("booking-closed", "This booking was cancelled.");
            case ARRIVED, PAST -> throw ApiException.conflict("booking-closed",
                    "This visit has already started, so it can't be changed here.");
            case TOO_LATE, LOCATION_CLOSED -> throw ApiException.conflict("too-late-to-change",
                    "It's too close to your visit to change it online. Please call the shop.");
        }
    }

    private Appointment find(String token) {
        if (!ManageTokens.looksValid(token)) {
            throw invalidLink();
        }
        return appointments.findByManageTokenHash(ManageTokens.hash(token)).orElseThrow(ManageBooking::invalidLink);
    }

    static ApiException invalidLink() {
        return new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "booking-link-invalid", "Not Found",
                "This link isn't valid any more. Use the link in your latest e-mail from the shop.");
    }
}
