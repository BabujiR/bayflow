package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.application.ApiException;

/** At most {@link #MAX_PER_HOUR} online bookings an hour from one address (per instance). */
@Component
public class PublicBookingLimiter {

    public static final int MAX_PER_HOUR = 10;

    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public PublicBookingLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_HOUR) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too Many Requests",
                        "Too many bookings from here. Please call the shop, or try again later.");
            }
            times.addLast(now);
        }
    }

    /** Tests start from a clean slate. */
    public void reset() {
        recent.clear();
    }
}
