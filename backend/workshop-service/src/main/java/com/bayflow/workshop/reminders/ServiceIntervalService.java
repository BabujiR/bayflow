package com.bayflow.workshop.reminders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.reminders.ServiceInterval.Details;

/**
 * The shop's service intervals and reminder settings. A workshop starts with a few common
 * intervals (added once, the first time they're needed) that admins change or switch off.
 */
@Service
public class ServiceIntervalService {

    /** The starter list: common intervals for cars driven in the US. */
    static final List<Details> STARTERS = List.of(
            new Details("Oil change", 5000, 6, List.of("oil change", "oil and filter", "oil & filter", "lube"),
                    List.of(), true),
            new Details("Tire rotation", 7500, 6, List.of("tire rotation", "rotate tires", "rotation"), List.of(), true),
            new Details("Brake inspection", 12000, 12, List.of("brake inspection", "brake pads", "brake check"),
                    List.of(), true),
            new Details("Cabin air filter", 15000, 12, List.of("cabin filter", "cabin air"), List.of(), true),
            new Details("Coolant", 30000, 24, List.of("coolant", "antifreeze"), List.of(), true));

    private final ServiceIntervalRepository intervals;
    private final ReminderSettingsRepository settings;
    private final Clock clock;

    public ServiceIntervalService(ServiceIntervalRepository intervals, ReminderSettingsRepository settings,
            Clock clock) {
        this.intervals = intervals;
        this.settings = settings;
        this.clock = clock;
    }

    /** Every interval in order (the starter list on first use). */
    @Transactional
    public List<ServiceInterval> list() {
        settings();
        return intervals.findAllByOrderByPositionAscNameAsc();
    }

    @Transactional
    public ServiceInterval create(Details details) {
        int position = list().stream().mapToInt(ServiceInterval::getPosition).max().orElse(-1) + 1;
        return intervals.saveAndFlush(ServiceInterval.create(details, position, clock.instant()));
    }

    @Transactional
    public ServiceInterval update(UUID id, long version, Details details) {
        ServiceInterval interval = intervals.findById(id).orElseThrow(() -> ApiException.notFound("Service interval"));
        if (interval.getVersion() != null && interval.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "This interval was changed by someone else. Reload and try again.");
        }
        interval.update(details, clock.instant());
        return intervals.saveAndFlush(interval);
    }

    /** Removes the interval and what was recorded against it (switch it off to keep that). */
    @Transactional
    public void delete(UUID id) {
        intervals.delete(intervals.findById(id).orElseThrow(() -> ApiException.notFound("Service interval")));
    }

    /** The organization's settings, made (with the starter intervals) the first time. */
    @Transactional
    public ReminderSettings settings() {
        UUID tenant = TenantContext.require();
        Instant now = clock.instant();
        ReminderSettings current = settings.findById(tenant).orElseGet(() -> ReminderSettings.create(tenant, now));
        if (!current.isDefaultsAdded()) {
            int position = 0;
            for (Details starter : STARTERS) {
                intervals.save(ServiceInterval.create(starter, position++, now));
            }
            current.defaultsAdded(now);
            current = settings.saveAndFlush(current);
        }
        return current;
    }

    @Transactional
    public ReminderSettings updateSettings(long version, boolean enabled, int remindDaysBefore, int milesPerDay) {
        ReminderSettings current = settings();
        if (current.getVersion() != null && current.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The reminder settings were changed by someone else. Reload and try again.");
        }
        current.update(enabled, remindDaysBefore, milesPerDay, clock.instant());
        return settings.saveAndFlush(current);
    }
}
