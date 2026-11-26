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
 * Fixed one-minute windows of login attempts, keyed by username + client IP. Counted in the
 * database ({@link RateLimitStore}), so the limit holds across instances of auth-service.
 */
@Component
public class LoginRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final AuthProperties properties;
    private final Clock clock;
    private final RateLimitStore store;

    @Autowired
    public LoginRateLimiter(AuthProperties properties, Clock clock, RateLimitStore store) {
        this.properties = properties;
        this.clock = clock;
        this.store = store;
    }

    /** Counted in memory (unit tests). */
    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this(properties, clock, new InMemoryRateLimitStore());
    }

    /** Counts one attempt for {@code key}; throws when the limit for the current window is exceeded. */
    public void check(String key) {
        Instant now = clock.instant();
        if (store.hit("login|" + key, now, WINDOW) > properties.getLogin().getMaxAttemptsPerMinute()) {
            throw new TooManyRequestsException(RateLimits.retryAfter(now, WINDOW));
        }
    }
}
