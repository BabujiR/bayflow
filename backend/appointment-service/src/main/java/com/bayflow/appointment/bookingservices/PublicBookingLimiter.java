package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.ops.InMemoryRateLimitStore;
import com.bayflow.ops.RateLimitStore;

/**
 * At most {@link #MAX_PER_HOUR} online bookings an hour (a fixed clock hour) from one address.
 * Counted in the database ({@link RateLimitStore}), so the limit holds across instances.
 */
@Component
public class PublicBookingLimiter {

    public static final int MAX_PER_HOUR = 10;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final RateLimitStore store;

    @Autowired
    public PublicBookingLimiter(Clock clock, RateLimitStore store) {
        this.clock = clock;
        this.store = store;
    }

    /** Counted in memory (unit tests). */
    public PublicBookingLimiter(Clock clock) {
        this(clock, new InMemoryRateLimitStore());
    }

    public void check(String key) {
        if (store.hit("booking|" + key, clock.instant(), WINDOW) > MAX_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too Many Requests",
                    "Too many bookings from here. Please call the shop, or try again later.");
        }
    }

    /** Tests start from a clean slate. */
    public void reset() {
        store.clear();
    }
}
