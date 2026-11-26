package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.Locations;
import com.bayflow.appointment.bookingservices.BookingSettings.DayHours;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.infrastructure.AppointmentRepository;

/**
 * Each location's online booking hours, and the free times they give: a slot is free while
 * fewer than "cars per slot" active bookings start in it, it is at least the notice away, and it
 * is no further ahead than allowed. Times are the location's local time.
 */
@Service
public class BookingHours {

    /** Most days one availability request covers. */
    public static final int MAX_DAYS_PER_REQUEST = 14;

    /** One day's free start times (empty: closed or full). */
    public record Day(LocalDate date, List<Instant> slots) {
    }

    private final BookingSettingsRepository settings;
    private final AppointmentRepository appointments;
    private final Locations locations;
    private final Clock clock;

    public BookingHours(BookingSettingsRepository settings, AppointmentRepository appointments, Locations locations,
            Clock clock) {
        this.settings = settings;
        this.appointments = appointments;
        this.locations = locations;
        this.clock = clock;
    }

    /** The location's settings, or the defaults while it has none. */
    @Transactional(readOnly = true)
    public BookingSettings settingsFor(UUID locationId) {
        return settings.findById(locationId).orElseGet(() -> BookingSettings.defaults(locationId));
    }

    /** {@code version}: the one read (0 for defaults never saved); 409 when someone saved meanwhile. */
    @Transactional
    public BookingSettings update(UUID locationId, long version, List<DayHours> week, int slotMinutes,
            int carsPerSlot, int minNoticeMinutes, int maxDaysAhead) {
        locations.requireActive(locationId);
        if (!BookingSettings.SLOT_LENGTHS.contains(slotMinutes)) {
            throw ApiException.badRequest("invalid-slot", "Choose a slot length of 15 minutes to 2 hours.");
        }
        if (carsPerSlot < 1 || carsPerSlot > 50 || minNoticeMinutes < 0 || minNoticeMinutes > 10080
                || maxDaysAhead < 1 || maxDaysAhead > 365) {
            throw ApiException.badRequest("invalid-booking-settings",
                    "Cars per slot 1-50, notice up to 7 days, and 1-365 days ahead.");
        }
        BookingSettings current = settingsFor(locationId);
        if (current.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "Someone else changed these hours. Reload and try again.");
        }
        current.update(week, slotMinutes, carsPerSlot, minNoticeMinutes, maxDaysAhead, clock.instant());
        return settings.saveAndFlush(current);
    }

    /** Free start times from {@code from} for up to {@code days} days (never past "days ahead"). */
    @Transactional(readOnly = true)
    public List<Day> freeSlots(UUID locationId, ZoneId zone, LocalDate from, int days) {
        BookingSettings s = settingsFor(locationId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate first = from.isBefore(today) ? today : from;
        LocalDate last = today.plusDays(s.getMaxDaysAhead());
        List<Day> result = new ArrayList<>();
        int wanted = Math.min(days, MAX_DAYS_PER_REQUEST);
        for (LocalDate date = first; !date.isAfter(last) && result.size() < wanted; date = date.plusDays(1)) {
            result.add(new Day(date, freeOn(locationId, zone, s, date, now)));
        }
        return result;
    }

    /** Whether {@code start} is one of the free times right now (checked again when booking). */
    @Transactional(readOnly = true)
    public boolean isFree(UUID locationId, ZoneId zone, Instant start) {
        BookingSettings s = settingsFor(locationId);
        Instant now = clock.instant();
        LocalDate date = LocalDate.ofInstant(start, zone);
        if (date.isAfter(LocalDate.ofInstant(now, zone).plusDays(s.getMaxDaysAhead()))) {
            return false;
        }
        return freeOn(locationId, zone, s, date, now).contains(start);
    }

    private List<Instant> freeOn(UUID locationId, ZoneId zone, BookingSettings s, LocalDate date, Instant now) {
        DayHours hours = s.week().get(date.getDayOfWeek().getValue() - 1);
        if (hours == null) {
            return List.of();
        }
        Instant earliest = now.plus(Duration.ofMinutes(s.getMinNoticeMinutes()));
        Duration slot = Duration.ofMinutes(s.getSlotMinutes());
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        List<Instant> taken = appointments.startsAt(locationId, AppointmentStatus.ACTIVE, dayStart,
                date.plusDays(1).atStartOfDay(zone).toInstant());
        List<Instant> free = new ArrayList<>();
        ZonedDateTime closes = date.atTime(hours.closes()).atZone(zone);
        for (ZonedDateTime t = date.atTime(hours.opens()).atZone(zone); !t.plus(slot).isAfter(closes);
                t = t.plus(slot)) {
            Instant start = t.toInstant();
            Instant end = start.plus(slot);
            if (start.isBefore(earliest)) {
                continue;
            }
            long booked = taken.stream().filter(b -> !b.isBefore(start) && b.isBefore(end)).count();
            if (booked < s.getCarsPerSlot()) {
                free.add(start);
            }
        }
        return free;
    }
}
