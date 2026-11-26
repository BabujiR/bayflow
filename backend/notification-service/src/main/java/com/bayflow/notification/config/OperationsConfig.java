package com.bayflow.notification.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bayflow.ops.JobLock;

/**
 * What lets several instances of this service run side by side: scheduled jobs run on one
 * instance at a time (ntf_job_locks).
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
        return new JobLock(dataSource, schema + ".ntf_job_locks");
    }
}
