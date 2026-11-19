package com.bayflow.workshop.config;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

import com.bayflow.security.RowLevelSecurityGuard;
import com.bayflow.security.TenantAwareJpaTransactionManager;

/**
 * Multi-tenancy at the database: every transaction tells PostgreSQL its workshop (row-level
 * security), and the service refuses to start with a login that RLS would not apply to.
 */
@Configuration(proxyBeanMethods = false)
public class TenancyConfig {

    /** Replaces Spring Boot's JPA transaction manager (it backs off when one is defined). */
    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }

    @Bean
    RowLevelSecurityGuard rowLevelSecurityGuard(DataSource dataSource) {
        return new RowLevelSecurityGuard(dataSource, "bayflow");
    }
}
