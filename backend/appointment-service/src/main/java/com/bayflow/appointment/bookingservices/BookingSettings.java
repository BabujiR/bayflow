package com.bayflow.appointment.bookingservices;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * When one location takes online bookings: weekly hours, slot length, cars that may arrive per
 * slot, notice needed and how far ahead. A location without settings uses {@link #defaults}.
 */
@Entity
@Table(name = "apt_booking_settings")
public class BookingSettings {

    /** One day's opening hours; {@code null} in a week means closed. */
    public record DayHours(LocalTime opens, LocalTime closes) {
        public DayHours {
            if (!closes.isAfter(opens)) {
                throw new IllegalArgumentException("A day closes after it opens");
            }
        }
    }

    public static final List<Integer> SLOT_LENGTHS = List.of(15, 20, 30, 45, 60, 90, 120);

    @Id
    @Column(name = "location_id")
    private UUID locationId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** "08:00-17:00|08:00-17:00|…" Monday to Sunday; empty = closed. */
    @Column(name = "weekly_hours", nullable = false, length = 120)
    private String weeklyHours;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "cars_per_slot", nullable = false)
    private int carsPerSlot;

    @Column(name = "min_notice_minutes", nullable = false)
    private int minNoticeMinutes;

    @Column(name = "max_days_ahead", nullable = false)
    private int maxDaysAhead;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected BookingSettings() {
        // for JPA
    }

    /** Monday to Friday, 8 to 5; 30-minute slots; 2 cars per slot; 2 hours' notice; 30 days ahead. */
    public static BookingSettings defaults(UUID locationId) {
        BookingSettings s = new BookingSettings();
        s.locationId = locationId;
        DayHours day = new DayHours(LocalTime.of(8, 0), LocalTime.of(17, 0));
        s.weeklyHours = format(java.util.Arrays.asList(day, day, day, day, day, null, null));
        s.slotMinutes = 30;
        s.carsPerSlot = 2;
        s.minNoticeMinutes = 120;
        s.maxDaysAhead = 30;
        return s;
    }

    public void update(List<DayHours> week, int slotMinutes, int carsPerSlot, int minNoticeMinutes, int maxDaysAhead,
            Instant now) {
        if (week.size() != 7) {
            throw new IllegalArgumentException("Seven days, Monday first");
        }
        this.weeklyHours = format(week);
        this.slotMinutes = slotMinutes;
        this.carsPerSlot = carsPerSlot;
        this.minNoticeMinutes = minNoticeMinutes;
        this.maxDaysAhead = maxDaysAhead;
        this.updatedAt = now;
    }

    /** Monday first; {@code null} = closed. */
    public List<DayHours> week() {
        String[] days = weeklyHours.split("\\|", -1);
        List<DayHours> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            String day = i < days.length ? days[i] : "";
            if (day.isBlank()) {
                week.add(null);
            } else {
                String[] range = day.split("-");
                week.add(new DayHours(LocalTime.parse(range[0]), LocalTime.parse(range[1])));
            }
        }
        return week;
    }

    private static String format(List<DayHours> week) {
        return String.join("|", week.stream()
                .map(d -> d == null ? "" : d.opens() + "-" + d.closes())
                .toList());
    }

    public UUID getLocationId() { return locationId; }
    public int getSlotMinutes() { return slotMinutes; }
    public int getCarsPerSlot() { return carsPerSlot; }
    public int getMinNoticeMinutes() { return minNoticeMinutes; }
    public int getMaxDaysAhead() { return maxDaysAhead; }
    public Instant getUpdatedAt() { return updatedAt; }
    /** 0 for defaults not yet saved. */
    public long getVersion() { return version == null ? 0 : version; }
    public boolean isSaved() { return version != null; }
}
