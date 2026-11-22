package com.bayflow.workshop.invoicing;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-free invoice numbers per organization (INV-1001, INV-1002, …). The counter row stays
 * locked until the issuing transaction ends, so two advisors never get the same number, and a
 * rolled-back issue gives its number back.
 */
@Component
public class InvoiceNumbers {

    static final long FIRST = 1001;
    static final String PREFIX = "INV-";

    @PersistenceContext
    private EntityManager em;

    /** Must run inside the issuing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID tenantId) {
        em.createNativeQuery("""
                INSERT INTO {h-schema}ws_invoice_counters (tenant_id, next_number) VALUES (:tenant, :first)
                ON CONFLICT (tenant_id) DO NOTHING
                """).setParameter("tenant", tenantId).setParameter("first", FIRST).executeUpdate();
        Number taken = (Number) em.createNativeQuery("""
                UPDATE {h-schema}ws_invoice_counters SET next_number = next_number + 1
                WHERE tenant_id = :tenant
                RETURNING next_number - 1
                """).setParameter("tenant", tenantId).getSingleResult();
        return PREFIX + taken.longValue();
    }
}
