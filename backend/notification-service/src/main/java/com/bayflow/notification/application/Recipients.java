package com.bayflow.notification.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.bayflow.notification.integration.AuthClient;
import com.bayflow.security.Roles;

/**
 * Who is told about an event, and in which voice: the customer about their own car, or the
 * staff of the location it happened at. Nobody is told about their own action.
 */
@Component
public class Recipients {

    /** Whom a notification speaks to: the customer, front-desk staff or mechanics. */
    public enum Audience { CUSTOMER, DESK, MECHANIC }

    static final List<String> DESK_ROLES = List.of(Roles.SERVICE_ADVISOR, Roles.MANAGER);
    static final List<String> MECHANIC_ROLES = List.of(Roles.MECHANIC);

    private final AuthClient auth;

    public Recipients(AuthClient auth) {
        this.auth = auth;
    }

    /** Each recipient with the audience they are told as (the first one wins if they are in two). */
    public Map<UUID, Audience> of(NotificationEvent event) {
        Map<UUID, Audience> recipients = new LinkedHashMap<>();
        switch (event.type()) {
            case APPOINTMENT_BOOKED, APPOINTMENT_RESCHEDULED, APPOINTMENT_CANCELLED -> {
                if (event.byCustomer()) {
                    add(recipients, staff(event, DESK_ROLES), Audience.DESK);
                } else {
                    addCustomer(recipients, event);
                }
            }
            case APPOINTMENT_CONFIRMED, ESTIMATE_READY, WORK_COMPLETED -> addCustomer(recipients, event);
            case VEHICLE_CHECKED_IN -> {
                addCustomer(recipients, event);
                add(recipients, staff(event, MECHANIC_ROLES), Audience.MECHANIC);
            }
            case ESTIMATE_DECIDED -> {
                add(recipients, staff(event, DESK_ROLES), Audience.DESK);
                if (event.approved() > 0) {
                    add(recipients, staff(event, MECHANIC_ROLES), Audience.MECHANIC);
                }
            }
        }
        if (event.actorUserId() != null) {
            recipients.remove(event.actorUserId());
        }
        return recipients;
    }

    private Set<UUID> staff(NotificationEvent event, List<String> roles) {
        if (event.locationId() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(auth.staffAt(event.tenantId(), event.locationId(), roles));
    }

    private static void addCustomer(Map<UUID, Audience> recipients, NotificationEvent event) {
        if (event.customerUserId() != null) {
            recipients.putIfAbsent(event.customerUserId(), Audience.CUSTOMER);
        }
    }

    private static void add(Map<UUID, Audience> recipients, Set<UUID> users, Audience audience) {
        users.forEach(user -> recipients.putIfAbsent(user, audience));
    }
}
