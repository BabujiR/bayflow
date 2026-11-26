package com.bayflow.auth.application;

import java.time.Duration;
import java.time.Instant;

import com.bayflow.ops.RateLimitStore;

/** Shared by the limiters here. */
final class RateLimits {

    private RateLimits() {
    }

    /** Until the next fixed window starts; at least a second. */
    static Duration retryAfter(Instant now, Duration window) {
        Duration left = Duration.between(now, RateLimitStore.windowStart(now, window).plus(window));
        return left.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : left;
    }
}
