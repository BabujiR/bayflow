package com.bayflow.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Counts in this instance's memory: for a single instance and unit tests. */
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int hits) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public int hit(String key, Instant now, Duration window) {
        Instant start = RateLimitStore.windowStart(now, window);
        Window counted = windows.compute(key, (k, current) -> current == null || !current.start().equals(start)
                ? new Window(start, 1) : new Window(start, current.hits() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(window)));
        }
        return counted.hits();
    }

    @Override
    public void clear() {
        windows.clear();
    }
}
