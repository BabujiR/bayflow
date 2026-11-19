package com.bayflow.notification.config;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.bayflow.security.Roles;

/**
 * Whose notifications the current transaction may touch, passed to PostgreSQL as
 * {@code app.recipient} (row-level security):
 * <ul>
 * <li>inside {@link #asService}: every recipient's ({@code *}), for storing an event's notifications;</li>
 * <li>otherwise, for a signed-in user: only their own (the token's subject);</li>
 * <li>otherwise (no user, or a SYSTEM token outside {@link #asService}): nobody's.</li>
 * </ul>
 */
public final class RecipientContext {

    public static final String ALL = "*";

    private static final ThreadLocal<Boolean> SERVICE = new ThreadLocal<>();

    private RecipientContext() {
    }

    /** Runs {@code work} as this service itself, for every recipient of the current workshop. */
    public static <T> T asService(Supplier<T> work) {
        Boolean previous = SERVICE.get();
        SERVICE.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SERVICE.remove();
            } else {
                SERVICE.set(previous);
            }
        }
    }

    /** The signed-in user (not a SYSTEM caller), if any. */
    public static Optional<UUID> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        boolean system = token.getAuthorities().stream()
                .anyMatch(a -> Roles.authority(Roles.SYSTEM).equals(a.getAuthority()));
        if (system) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(token.getToken().getSubject()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /** The value for {@code app.recipient}: "*", a user id, or "" for none. */
    public static String asSetting() {
        if (Boolean.TRUE.equals(SERVICE.get())) {
            return ALL;
        }
        return currentUser().map(UUID::toString).orElse("");
    }
}
