package com.bayflow.security;

import java.sql.PreparedStatement;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells PostgreSQL which workshop every transaction works for, so the row-level security
 * policies on the per-workshop tables let through only that workshop's rows.
 * <p>
 * Right after a transaction starts it runs {@code set_config('app.tenant', <tenant>, true)}.
 * The {@code true} makes the setting local to the transaction: when it ends, the pooled
 * connection forgets it, so the next request on the same connection starts with no workshop.
 * When no workshop is known (or inside {@link TenantContext#callAcrossTenants}), the setting is
 * empty and the policies match no rows.
 * <p>
 * Each service declares it as its {@code transactionManager} bean; Spring Boot's own JPA
 * transaction manager then backs off.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    static final String SET_TENANT = "select set_config('app.tenant', ?, true)";

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager
                .getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("No EntityManager bound to the new transaction");
        }
        String tenant = TenantContext.isAcrossTenants()
                ? ""
                : TenantContext.current().map(UUID::toString).orElse("");
        holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
                statement.setString(1, tenant);
                statement.execute();
            }
        });
    }
}
