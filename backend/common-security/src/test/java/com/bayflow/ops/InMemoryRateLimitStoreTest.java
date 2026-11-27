package com.bayflow.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore();

    @Test
    void windowsAreAlignedToTheirLength() {
        assertThat(RateLimitStore.windowStart(Instant.parse("2026-11-09T10:00:59Z"), MINUTE))
                .isEqualTo(Instant.parse("2026-11-09T10:00:00Z"));
        assertThat(RateLimitStore.windowStart(Instant.parse("2026-11-09T10:59:59Z"), Duration.ofHours(1)))
                .isEqualTo(Instant.parse("2026-11-09T10:00:00Z"));
    }

    @Test
    void countsPerKeyAndStartsAgainInTheNextWindow() {
        Instant now = Instant.parse("2026-11-09T10:00:10Z");
        assertThat(store.hit("a", now, MINUTE)).isEqualTo(1);
        assertThat(store.hit("a", now.plusSeconds(40), MINUTE)).isEqualTo(2);
        assertThat(store.hit("b", now, MINUTE)).isEqualTo(1);
        assertThat(store.hit("a", now.plusSeconds(50), MINUTE)).isEqualTo(1);
    }

    @Test
    void clearForgetsEverything() {
        Instant now = Instant.parse("2026-11-09T10:00:10Z");
        store.hit("a", now, MINUTE);
        store.clear();
        assertThat(store.hit("a", now, MINUTE)).isEqualTo(1);
    }
}
