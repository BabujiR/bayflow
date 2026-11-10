package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.bayflow.auth.config.AuthProperties;

/**
 * In-memory fixed-window limiter for login attempts, keyed by username + client IP.
 * Good enough for a single auth-service instance; a shared store (e.g. Redis) would be
 * needed when running several instances.
 */
@Component
public class LoginRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one attempt for {@code key}; throws when the limit for the current window is exceeded. */
    public void check(String key) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, current) ->
                current == null || !now.isBefore(current.start().plus(WINDOW))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(WINDOW)));
        }
        if (window.count() > properties.getLogin().getMaxAttemptsPerMinute()) {
            Duration retryAfter = Duration.between(now, window.start().plus(WINDOW));
            throw new TooManyRequestsException(retryAfter.isNegative() || retryAfter.isZero()
                    ? Duration.ofSeconds(1) : retryAfter);
        }
    }
}
