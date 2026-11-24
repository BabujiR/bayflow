package com.bayflow.workshop.reminders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.application.OutboxService;

/**
 * Tells customers when their vehicle is due for maintenance: once a day for every organization
 * (one reminder per vehicle, listing every service due within the shop's "days before", each due
 * date reminded once), or straight away when staff click Send reminder. Customers who turned
 * reminders off, and walk-ins without an account, are left out.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    /** The widest "days before" a shop can set: organizations with nothing due that soon are skipped. */
    static final int MAX_DAYS_BEFORE = 90;
    /** Send reminder (staff) covers services due within this many days, or overdue. */
    static final int MANUAL_DAYS = 60;

    private final VehicleServiceRepository vehicles;
    private final ServiceIntervalRepository intervals;
    private final ServiceIntervalService setup;
    private final ReminderOptOutRepository optOuts;
    private final OutboxService outbox;
    private final Clock clock;

    public ReminderService(VehicleServiceRepository vehicles, ServiceIntervalRepository intervals,
            ServiceIntervalService setup, ReminderOptOutRepository optOuts, OutboxService outbox, Clock clock) {
        this.vehicles = vehicles;
        this.intervals = intervals;
        this.setup = setup;
        this.optOuts = optOuts;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Organizations that may have reminders to send today. Not transactional itself: the read
     * across organizations must be in place before the repository's transaction starts.
     */
    public List<UUID> tenantsToRemind() {
        return TenantContext.callAcrossTenants(() -> vehicles.tenantsWithDueReminders(
                clock.instant().plus(Duration.ofDays(MAX_DAYS_BEFORE))));
    }

    /** Today's reminders of the current organization; the number of vehicles reminded. */
    @Transactional
    public int remindDue() {
        ReminderSettings settings = setup.settings();
        if (!settings.isEnabled()) {
            return 0;
        }
        Instant now = clock.instant();
        List<VehicleService> due = vehicles.findDueBefore(now.plus(Duration.ofDays(settings.getRemindDaysBefore())))
                .stream().filter(v -> v.getRemindedAt() == null && v.getCustomerUserId() != null).toList();
        return send(due, now);
    }

    /** Send reminder (staff): the vehicle's services due within 60 days, reminded or not. */
    @Transactional
    public void remindNow(UUID vehicleId) {
        Instant now = clock.instant();
        Instant soon = now.plus(Duration.ofDays(MANUAL_DAYS));
        Set<UUID> active = activeIntervals().keySet();
        List<VehicleService> due = vehicles.findByVehicleId(vehicleId).stream()
                .filter(v -> active.contains(v.getIntervalId()) && v.getDueAt().isBefore(soon)).toList();
        if (due.isEmpty()) {
            throw ApiException.notFound("Service due for this vehicle");
        }
        UUID customer = latest(due).getCustomerUserId();
        if (customer == null) {
            throw ApiException.conflict("no-customer-account",
                    "This customer has no account to send a reminder to. Call them instead.");
        }
        if (!optOuts.optedOutAmong(List.of(customer)).isEmpty()) {
            throw ApiException.conflict("reminders-off", "This customer turned service reminders off.");
        }
        send(due, now);
    }

    /** Whether the signed-in customer gets reminders. */
    @Transactional(readOnly = true)
    public boolean isEnabledFor(UUID customerUserId) {
        return optOuts.optedOutAmong(List.of(customerUserId)).isEmpty();
    }

    @Transactional
    public void setEnabledFor(UUID customerUserId, boolean enabled) {
        ReminderOptOut.Key key = new ReminderOptOut.Key(TenantContext.require(), customerUserId);
        if (enabled) {
            optOuts.findById(key).ifPresent(optOuts::delete);
        } else if (!optOuts.existsById(key)) {
            optOuts.save(ReminderOptOut.of(key.tenantId(), customerUserId, clock.instant()));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** One reminder per vehicle (to its latest customer), listing its services; marks them reminded. */
    private int send(List<VehicleService> due, Instant now) {
        Map<UUID, ServiceInterval> byId = activeIntervals();
        Map<UUID, List<VehicleService>> perVehicle = new LinkedHashMap<>();
        for (VehicleService v : due) {
            if (byId.containsKey(v.getIntervalId())) {
                perVehicle.computeIfAbsent(v.getVehicleId(), id -> new ArrayList<>()).add(v);
            }
        }
        Set<UUID> customers = new HashSet<>();
        perVehicle.values().forEach(list -> customers.add(latest(list).getCustomerUserId()));
        customers.remove(null);
        Set<UUID> off = customers.isEmpty() ? Set.of() : new HashSet<>(optOuts.optedOutAmong(customers));
        int sent = 0;
        for (List<VehicleService> services : perVehicle.values()) {
            VehicleService latest = latest(services);
            if (latest.getCustomerUserId() == null || off.contains(latest.getCustomerUserId())) {
                continue;
            }
            // Soonest first; services due together in the shop's order.
            String names = services.stream()
                    .sorted(Comparator.comparing(VehicleService::getDueAt)
                            .thenComparingInt(v -> byId.get(v.getIntervalId()).getPosition()))
                    .map(v -> byId.get(v.getIntervalId()).getName()).distinct().collect(Collectors.joining(", "));
            outbox.enqueueServiceDue(latest.getLocationId(), latest.getCustomerUserId(), latest.getJobCardId(),
                    latest.getRegistration(), names);
            services.forEach(v -> v.reminded(now));
            sent++;
        }
        if (sent > 0) {
            log.info("Service reminders sent for {} vehicle(s)", sent);
        }
        return sent;
    }

    private Map<UUID, ServiceInterval> activeIntervals() {
        return intervals.findAll().stream().filter(ServiceInterval::isActive)
                .collect(Collectors.toMap(ServiceInterval::getId, Function.identity()));
    }

    private static VehicleService latest(List<VehicleService> services) {
        return services.stream().max(Comparator.comparing(VehicleService::getLastDoneAt)).orElseThrow();
    }
}
