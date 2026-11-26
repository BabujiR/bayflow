package com.bayflow.ops;

import java.time.Duration;
import java.time.Instant;

/**
 * Counts hits per key in fixed windows ({@code windowStart} = the time truncated to the window's
 * length). {@link JdbcRateLimitStore} shares the counts between instances of a service;
 * {@link InMemoryRateLimitStore} is for one instance and unit tests.
 */
public interface RateLimitStore {

    /** Counts one hit for {@code key} in its current window; the window's hits, this one included. */
    int hit(String key, Instant now, Duration window);

    /** Forgets every count (tests). */
    void clear();

    /** The start of the fixed window {@code now} falls in. */
    static Instant windowStart(Instant now, Duration window) {
        long length = window.toMillis();
        return Instant.ofEpochMilli(Math.floorDiv(now.toEpochMilli(), length) * length);
    }
}
