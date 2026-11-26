package com.bayflow.workshop.infrastructure;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * The outbox in Prometheus, across every workshop: {@code bayflow_outbox_pending} (waiting to be
 * delivered), {@code bayflow_outbox_failed} (given up on: needs an operator) and
 * {@code bayflow_outbox_oldest_pending_seconds}. Read through the owner-run {@code ws_outbox_stats}
 * function, at most every 15 seconds however often it's scraped.
 */
@Component
public class OutboxMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);
    private static final Duration FRESH_FOR = Duration.ofSeconds(15);

    private record Stats(long pending, long failed, double oldestPendingSeconds, Instant readAt) {
    }

    private final DataSource dataSource;
    private final String function;
    private final Clock clock;
    private volatile Stats stats;

    public OutboxMetrics(DataSource dataSource, Clock clock,
            @Value("${spring.flyway.default-schema:bayflow}") String schema) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.function = schema + ".ws_outbox_stats()";
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder("bayflow.outbox.pending", this, m -> m.read().pending())
                .description("Outbox messages waiting to be delivered").register(registry);
        Gauge.builder("bayflow.outbox.failed", this, m -> m.read().failed())
                .description("Outbox messages given up on after too many attempts").register(registry);
        Gauge.builder("bayflow.outbox.oldest.pending", this, m -> m.read().oldestPendingSeconds())
                .baseUnit("seconds").description("Age of the oldest message waiting to be delivered")
                .register(registry);
    }

    private Stats read() {
        Instant now = clock.instant();
        Stats current = stats;
        if (current != null && current.readAt().plus(FRESH_FOR).isAfter(now)) {
            return current;
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select * from " + function)) {
            result.next();
            current = new Stats(result.getLong(1), result.getLong(2), result.getDouble(3), now);
        } catch (SQLException e) {
            log.warn("Could not read the outbox numbers: {}", e.getMessage());
            current = new Stats(-1, -1, -1, now);
        }
        stats = current;
        return current;
    }
}
