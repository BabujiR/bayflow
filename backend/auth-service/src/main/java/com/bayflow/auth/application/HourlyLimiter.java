package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Counts events per key in fixed one-hour windows (in memory, per instance), e.g. password-reset
 * e-mails per address and requests per IP address.
 */
@Component
public class HourlyLimiter {

    static final Duration WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public HourlyLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one event for {@code key}; false once more than {@code limit} happened this hour. */
    public boolean tryAcquire(String key, int limit) {
        return window(key).count() <= limit;
    }

    /** Counts one event for {@code key}; throws 429 once more than {@code limit} happened this hour. */
    public void check(String key, int limit) {
        Window window = window(key);
        if (window.count() > limit) {
            Duration retryAfter = Duration.between(clock.instant(), window.start().plus(WINDOW));
            throw new TooManyRequestsException(retryAfter.isNegative() || retryAfter.isZero()
                    ? Duration.ofSeconds(1) : retryAfter);
        }
    }

    /** Forgets every window (tests). */
    public void reset() {
        windows.clear();
    }

    private Window window(String key) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, current) ->
                current == null || !now.isBefore(current.start().plus(WINDOW))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(WINDOW)));
        }
        return window;
    }
}
