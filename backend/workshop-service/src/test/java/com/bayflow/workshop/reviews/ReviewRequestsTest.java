package com.bayflow.workshop.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.bayflow.workshop.config.WorkshopProperties;

class ReviewRequestsTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private final ReviewRequests reviews = new ReviewRequests(null, null, null, new WorkshopProperties(), null,
            Clock.systemUTC());

    @Test
    void whenARequestGoesOut() {
        // Paid Friday 3:23 PM in New York.
        Instant paid = Instant.parse("2026-11-27T20:23:00Z");
        assertThat(reviews.dueAt("TWO_HOURS", NEW_YORK, paid)).isEqualTo(Instant.parse("2026-11-27T22:23:00Z"));
        assertThat(reviews.dueAt("TWO_DAYS", NEW_YORK, paid)).isEqualTo(Instant.parse("2026-11-29T20:23:00Z"));
        // The next morning at 9 there (EST, UTC-5).
        assertThat(reviews.dueAt("NEXT_MORNING", NEW_YORK, paid)).isEqualTo(Instant.parse("2026-11-28T14:00:00Z"));
        // Paid late at night: still the next calendar day's morning there, not the same day's.
        assertThat(reviews.dueAt("NEXT_MORNING", NEW_YORK, Instant.parse("2026-11-28T04:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-28T14:00:00Z"));
        assertThat(reviews.dueAt(null, NEW_YORK, paid)).isEqualTo(Instant.parse("2026-11-28T14:00:00Z"));
    }
}
