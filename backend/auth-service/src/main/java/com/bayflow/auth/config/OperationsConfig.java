package com.bayflow.auth.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bayflow.ops.JobLock;
import com.bayflow.ops.JdbcRateLimitStore;
import com.bayflow.ops.RateLimitStore;

/**
 * What lets several instances of this service run side by side: scheduled jobs run on one
 * instance at a time (auth_job_locks), and rate limits are counted in the
 * database (auth_rate_limits), not per instance.
 */
@Configuration
public class OperationsConfig {

    private final DataSource dataSource;
    private final String schema;

    public OperationsConfig(DataSource dataSource, @Value("${spring.flyway.default-schema:bayflow}") String schema) {
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Bean
    JobLock jobLock() {
        return new JobLock(dataSource, schema + ".auth_job_locks");
    }

    @Bean
    RateLimitStore rateLimitStore() {
        return new JdbcRateLimitStore(dataSource, schema + ".auth_rate_limits");
    }
}
