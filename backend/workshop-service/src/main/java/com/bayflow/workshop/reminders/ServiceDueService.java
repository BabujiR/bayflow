package com.bayflow.workshop.reminders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.LocationContext;
import com.bayflow.security.LocationScope;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.RecommendedWorkItem;
import com.bayflow.workshop.domain.ServiceHistoryEntry;
import com.bayflow.workshop.infrastructure.ServiceHistoryRepository;

/**
 * When each vehicle's services are next due. A finished job records every interval its approved
 * work counts as (by canned job or keyword); the next due date is whichever comes first, the
 * interval's months or its miles at the vehicle's own miles a day (from its first visit), or the
 * shop's assumed miles a day for a vehicle seen once.
 */
@Service
public class ServiceDueService {

    private static final Logger log = LoggerFactory.getLogger(ServiceDueService.class);
    /** Mileage history over less than this says too little about how much a vehicle is driven. */
    static final Duration MIN_HISTORY = Duration.ofDays(30);
    /** At check-in: due within this many days, or within this many miles. */
    static final int CHECK_IN_DAYS = 30;
    static final int CHECK_IN_MILES = 500;

    /** One service of a vehicle and when it is due. */
    public record DueService(UUID intervalId, String name, Instant lastDoneAt, int lastMileage, Integer dueMileage,
            Instant dueAt, boolean overdue, UUID cannedJobId) {
    }

    /** A vehicle with services due, for the Due soon list. */
    public record DueVehicle(UUID vehicleId, String registration, String customerName, UUID customerUserId,
            UUID locationId, Instant dueAt, Instant remindedAt, List<DueService> services) {
    }

    private final VehicleServiceRepository vehicles;
    private final ServiceIntervalRepository intervals;
    private final ServiceIntervalService setup;
    private final ServiceHistoryRepository history;
    private final Clock clock;

    public ServiceDueService(VehicleServiceRepository vehicles, ServiceIntervalRepository intervals,
            ServiceIntervalService setup, ServiceHistoryRepository history, Clock clock) {
        this.vehicles = vehicles;
        this.intervals = intervals;
        this.setup = setup;
        this.history = history;
        this.clock = clock;
    }

    /** Called when a job card is completed, in its transaction (after its service history entry). */
    @Transactional
    public void recordCompleted(JobCard card) {
        List<RecommendedWorkItem> done = card.getRecommendations().stream()
                .filter(RecommendedWorkItem::isApproved).toList();
        if (done.isEmpty() || card.getVehicleId() == null) {
            return;
        }
        ReminderSettings settings = setup.settings();
        Instant now = clock.instant();
        Instant doneAt = card.getCompletedAt() != null ? card.getCompletedAt() : now;
        double milesPerDay = milesPerDay(card, doneAt, settings.getMilesPerDay());
        for (ServiceInterval interval : intervals.findAllByOrderByPositionAscNameAsc()) {
            if (!interval.isActive()
                    || done.stream().noneMatch(item -> interval.matches(item.getCannedJobId(), item.getDescription()))) {
                continue;
            }
            Integer dueMileage = interval.getMiles() == null ? null : card.getMileage() + interval.getMiles();
            VehicleService.Done record = new VehicleService.Done(card.getLocationId(), card.getCustomerUserId(),
                    card.getCustomerName(), card.getRegistration(), card.getId(), doneAt, card.getMileage(), dueMileage,
                    dueAt(interval, doneAt, milesPerDay));
            vehicles.findByVehicleIdAndIntervalId(card.getVehicleId(), interval.getId()).ifPresentOrElse(
                    existing -> existing.record(record, now),
                    () -> vehicles.save(VehicleService.create(card.getVehicleId(), interval.getId(), record, now)));
            log.debug("{} of vehicle {} recorded; next due {}", interval.getName(), card.getVehicleId(),
                    record.dueAt());
        }
    }

    /** Vehicles with a service due within {@code days} (or overdue), at the caller's locations. */
    @Transactional(readOnly = true)
    public List<DueVehicle> dueSoon(int days, UUID locationId) {
        Instant now = clock.instant();
        LocationScope scope = LocationContext.current();
        List<VehicleService> due = vehicles.findDueBefore(now.plus(Duration.ofDays(days))).stream()
                .filter(v -> scope.allows(v.getLocationId()))
                .filter(v -> locationId == null || locationId.equals(v.getLocationId()))
                .toList();
        Map<UUID, ServiceInterval> byId = intervals.findAll().stream()
                .collect(Collectors.toMap(ServiceInterval::getId, Function.identity()));
        Map<UUID, List<VehicleService>> perVehicle = new LinkedHashMap<>();
        for (VehicleService v : due) {
            perVehicle.computeIfAbsent(v.getVehicleId(), id -> new ArrayList<>()).add(v);
        }
        List<DueVehicle> out = new ArrayList<>();
        for (List<VehicleService> services : perVehicle.values()) {
            // The latest visit has the current owner, location and registration.
            VehicleService latest = services.stream().max(Comparator.comparing(VehicleService::getLastDoneAt))
                    .orElseThrow();
            Instant reminded = services.stream().map(VehicleService::getRemindedAt).filter(r -> r != null)
                    .max(Comparator.naturalOrder()).orElse(null);
            out.add(new DueVehicle(latest.getVehicleId(), latest.getRegistration(), latest.getCustomerName(),
                    latest.getCustomerUserId(), latest.getLocationId(), services.get(0).getDueAt(), reminded,
                    services.stream().map(v -> due(v, byId.get(v.getIntervalId()), now)).toList()));
        }
        return out;
    }

    /**
     * At check-in: the vehicle's services due within 30 days or 500 miles of {@code mileage}
     * (the odometer now), soonest first.
     */
    @Transactional(readOnly = true)
    public List<DueService> forVehicle(UUID vehicleId, Integer mileage) {
        Instant now = clock.instant();
        Instant soon = now.plus(Duration.ofDays(CHECK_IN_DAYS));
        Map<UUID, ServiceInterval> byId = intervals.findAll().stream().filter(ServiceInterval::isActive)
                .collect(Collectors.toMap(ServiceInterval::getId, Function.identity()));
        return vehicles.findByVehicleId(vehicleId).stream()
                .filter(v -> byId.containsKey(v.getIntervalId()))
                .filter(v -> v.getDueAt().isBefore(soon) || (mileage != null && v.getDueMileage() != null
                        && mileage >= v.getDueMileage() - CHECK_IN_MILES))
                .sorted(Comparator.comparing(VehicleService::getDueAt))
                .map(v -> due(v, byId.get(v.getIntervalId()), now))
                .toList();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static DueService due(VehicleService v, ServiceInterval interval, Instant now) {
        String name = interval == null ? "Service" : interval.getName();
        UUID cannedJob = interval == null || interval.getCannedJobIds().isEmpty() ? null
                : interval.getCannedJobIds().get(0);
        return new DueService(v.getIntervalId(), name, v.getLastDoneAt(), v.getLastMileage(), v.getDueMileage(),
                v.getDueAt(), v.getDueAt().isBefore(now), cannedJob);
    }

    /** The months, or the miles at {@code milesPerDay}: whichever comes first. */
    static Instant dueAt(ServiceInterval interval, Instant doneAt, double milesPerDay) {
        Instant byMonths = interval.getMonths() == null ? null
                : doneAt.atZone(ZoneOffset.UTC).plusMonths(interval.getMonths()).toInstant();
        Instant byMiles = interval.getMiles() == null ? null
                : doneAt.plus(Duration.ofDays((long) Math.ceil(interval.getMiles() / milesPerDay)));
        if (byMonths == null) {
            return byMiles;
        }
        return byMiles == null || byMonths.isBefore(byMiles) ? byMonths : byMiles;
    }

    /** From the vehicle's first visit (if long enough ago and the odometer went up), else the shop's guess. */
    private double milesPerDay(JobCard card, Instant doneAt, int fallback) {
        Optional<ServiceHistoryEntry> first = history.findFirstByVehicleIdOrderByServiceDateAsc(card.getVehicleId());
        if (first.isPresent()) {
            Duration span = Duration.between(first.get().getServiceDate(), doneAt);
            int miles = card.getMileage() - first.get().getMileage();
            if (span.compareTo(MIN_HISTORY) >= 0 && miles > 0) {
                double perDay = miles / (double) Math.max(1, span.toDays());
                return Math.min(500, Math.max(1, perDay));
            }
        }
        return fallback;
    }
}
