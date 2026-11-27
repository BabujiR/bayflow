package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.ops.InMemoryRateLimitStore;
import com.bayflow.ops.RateLimitStore;

/**
 * Workshop sign-ups per client IP, in fixed one-hour windows. Counted in the database
 * ({@link RateLimitStore}), so the limit holds across instances of auth-service.
 */
@Component
public class SignupRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);

    private final AuthProperties properties;
    private final Clock clock;
    private final RateLimitStore store;

    @Autowired
    public SignupRateLimiter(AuthProperties properties, Clock clock, RateLimitStore store) {
        this.properties = properties;
        this.clock = clock;
        this.store = store;
    }

    /** Counted in memory (unit tests). */
    public SignupRateLimiter(AuthProperties properties, Clock clock) {
        this(properties, clock, new InMemoryRateLimitStore());
    }

    /** Counts one sign-up attempt from {@code clientIp}; throws once the hourly limit is used up. */
    public void check(String clientIp) {
        Instant now = clock.instant();
        int hits = store.hit("signup|" + (clientIp == null ? "?" : clientIp), now, WINDOW);
        if (hits > properties.getSignup().getMaxPerHourPerIp()) {
            throw new TooManyRequestsException(RateLimits.retryAfter(now, WINDOW));
        }
    }

    /** Forgets every count (tests). */
    public void reset() {
        store.clear();
    }
}
