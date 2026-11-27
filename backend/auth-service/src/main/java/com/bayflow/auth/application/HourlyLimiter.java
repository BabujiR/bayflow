package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bayflow.ops.InMemoryRateLimitStore;
import com.bayflow.ops.RateLimitStore;

/**
 * Counts events per key in fixed one-hour windows, e.g. password-reset e-mails per address and
 * requests per IP address. Counted in the database ({@link RateLimitStore}), so the limits hold
 * across instances of auth-service.
 */
@Component
public class HourlyLimiter {

    static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final RateLimitStore store;

    @Autowired
    public HourlyLimiter(Clock clock, RateLimitStore store) {
        this.clock = clock;
        this.store = store;
    }

    /* Counted in memory (unit tests). */
    public HourlyLimiter(Clock clock) {
        this(clock, new InMemoryRateLimitStore());
    }

    /* Counts one event for {@code key}; false once more than {@code limit} happened this hour. */
    public boolean tryAcquire(String key, int limit) {
        return hit(key, clock.instant()) <= limit;
    }

    /* Counts one event for {@code key}; throws 429 once more than {@code limit} happened this hour. */
    public void check(String key, int limit) {
        Instant now = clock.instant();
        if (hit(key, now) > limit) {
            throw new TooManyRequestsException(RateLimits.retryAfter(now, WINDOW));
        }
    }

    /* Forgets every count (tests). */
    public void reset() {
        store.clear();
    }

    private int hit(String key, Instant now) {
        return store.hit("hourly|" + key, now, WINDOW);
    }
}
