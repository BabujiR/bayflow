package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.bayflow.auth.config.AuthProperties;

/**
 * In-memory fixed-window limiter for workshop sign-ups, per client IP per hour. Like
 * {@link LoginRateLimiter}, one auth-service instance only; several instances need a shared
 * store (see docs/SAAS-AWS-NOTES.md).
 */
@Component
public class SignupRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    public SignupRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts one sign-up attempt from {@code clientIp}; throws once the hourly limit is used up. */
    public void check(String clientIp) {
        Instant now = clock.instant();
        Window window = windows.compute(clientIp == null ? "?" : clientIp, (k, current) ->
                current == null || !now.isBefore(current.start().plus(WINDOW))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(WINDOW)));
        }
        if (window.count() > properties.getSignup().getMaxPerHourPerIp()) {
            Duration retryAfter = Duration.between(now, window.start().plus(WINDOW));
            throw new TooManyRequestsException(retryAfter.isNegative() || retryAfter.isZero()
                    ? Duration.ofSeconds(1) : retryAfter);
        }
    }

    /** Forgets every window (tests). */
    public void reset() {
        windows.clear();
    }
}
