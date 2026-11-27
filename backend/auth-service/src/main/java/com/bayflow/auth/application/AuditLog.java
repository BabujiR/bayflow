package com.bayflow.auth.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.AuditEvent;
import com.bayflow.auth.domain.AuditEvent.Action;
import com.bayflow.auth.domain.Location;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.AuditEventRepository;
import com.bayflow.auth.infrastructure.LocationRepository;
import com.bayflow.ops.RequestIds;
import com.bayflow.security.CurrentUser;

/**
 * The workshop's audit trail of account changes. An event is written in the same transaction as
 * the change, so there is never one without the other. The actor is the signed-in user.
 */
@Service
public class AuditLog {

    private final AuditEventRepository events;
    private final LocationRepository locations;
    private final Clock clock;

    public AuditLog(AuditEventRepository events, LocationRepository locations, Clock clock) {
        this.events = events;
        this.locations = locations;
        this.clock = clock;
    }

    /** Records {@code action} on {@code target}, by whoever is signed in. */
    @Transactional
    public void record(Action action, User target, String details) {
        UUID actorId = null;
        String actorName = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            CurrentUser actor = CurrentUser.from(authentication);
            actorId = parse(actor.subject());
            actorName = actor.username();
        }
        events.save(AuditEvent.of(action, actorId, actorName, target.getId(), target.getUsername(), details,
                RequestIds.current(), clock.instant()));
    }

    /** Newest first; for one account when {@code targetId} is given. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> list(UUID targetId, Pageable pageable) {
        return targetId == null
                ? events.findAllByOrderByOccurredAtDescIdDesc(pageable)
                : events.findByTargetIdOrderByOccurredAtDescIdDesc(targetId, pageable);
    }

    /** "Admin, Mechanic". */
    static String describe(Collection<Role> roles) {
        return roles.stream().sorted().map(AuditLog::label).collect(Collectors.joining(", "));
    }

    /** "every location", or the locations' names. */
    String describe(LocationAccess access) {
        if (access.all()) {
            return "every location";
        }
        List<String> names = locations.findAllById(access.locationIds()).stream().map(Location::getName)
                .sorted().toList();
        return names.isEmpty() ? "no location" : String.join(", ", names);
    }

    private static String label(Role role) {
        String name = role.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static UUID parse(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
