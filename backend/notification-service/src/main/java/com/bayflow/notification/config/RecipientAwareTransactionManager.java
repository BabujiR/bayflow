package com.bayflow.notification.config;

import java.sql.PreparedStatement;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bayflow.security.TenantAwareJpaTransactionManager;

/**
 * Like every Bayflow service, sets the workshop for row-level security on each transaction;
 * this service also sets whose notifications it may touch ({@code app.recipient}, see
 * {@link RecipientContext}), transaction-local like the workshop.
 */
public class RecipientAwareTransactionManager extends TenantAwareJpaTransactionManager {

    static final String SET_RECIPIENT = "select set_config('app.recipient', ?, true)";

    public RecipientAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
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
        String recipient = RecipientContext.asSetting();
        holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_RECIPIENT)) {
                statement.setString(1, recipient);
                statement.execute();
            }
        });
    }
}
