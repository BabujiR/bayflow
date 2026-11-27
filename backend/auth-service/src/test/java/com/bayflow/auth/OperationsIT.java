package com.bayflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.bayflow.ops.JdbcRateLimitStore;
import com.bayflow.ops.JobLock;
import com.bayflow.ops.RequestIds;

/** What lets several instances run side by side, and request ids. */
class OperationsIT extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Value("${spring.flyway.default-schema:bayflow}")
    private String schema;

    @Test
    void aJobRunsOnOneInstanceAtATime() {
        // Two instances: two locks on the same table.
        JobLock first = new JobLock(dataSource, schema + ".auth_job_locks");
        JobLock second = new JobLock(dataSource, schema + ".auth_job_locks");
        AtomicBoolean secondRan = new AtomicBoolean();

        boolean firstRan = first.runExclusively("operations-it", Duration.ZERO, Duration.ofMinutes(1),
                () -> second.runExclusively("operations-it", Duration.ZERO, Duration.ofMinutes(1),
                        () -> secondRan.set(true)));

        assertThat(firstRan).isTrue();
        assertThat(secondRan).isFalse();
        // Released: the next run goes ahead.
        assertThat(second.runExclusively("operations-it", Duration.ZERO, Duration.ofMinutes(1), () -> {
        })).isTrue();
    }

    @Test
    void aLockHeldAtLeastBlocksAnEarlyRerun() {
        JobLock first = new JobLock(dataSource, schema + ".auth_job_locks");
        JobLock second = new JobLock(dataSource, schema + ".auth_job_locks");

        first.runExclusively("operations-it-daily", Duration.ofMinutes(5), Duration.ofMinutes(10), () -> {
        });

        assertThat(second.runExclusively("operations-it-daily", Duration.ZERO, Duration.ofMinutes(1), () -> {
        })).isFalse();
    }

    @Test
    void rateLimitCountsAreSharedThroughTheDatabase() {
        // Two instances: two stores on the same table.
        var first = new JdbcRateLimitStore(dataSource, schema + ".auth_rate_limits");
        var second = new JdbcRateLimitStore(dataSource, schema + ".auth_rate_limits");
        Instant now = Instant.parse("2026-11-09T10:00:10Z");
        String key = "operations-it|" + System.nanoTime();

        assertThat(first.hit(key, now, Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(second.hit(key, now.plusSeconds(5), Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(first.hit(key, now.plusSeconds(60), Duration.ofMinutes(1))).isEqualTo(1);
        // Stored hashed: no addresses or user names in the table.
        String stored = "select count(*) from " + schema + ".auth_rate_limits where bucket like ?";
        assertThat(ownerJdbc().queryForObject(stored, Integer.class, "operations-it%")).isZero();
    }

    @Test
    void requestIdsAreKeptAndReturned() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestIds.HEADER, "it-request-0001"))
                .andExpect(header().string(RequestIds.HEADER, "it-request-0001"));
        mvc.perform(get("/actuator/health"))
                .andExpect(header().exists(RequestIds.HEADER));
    }
}
