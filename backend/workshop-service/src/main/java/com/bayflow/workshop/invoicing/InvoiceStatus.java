package com.bayflow.workshop.invoicing;

/**
 * DRAFT (editable, no number) → ISSUED (numbered, locked) → PARTIALLY_PAID → PAID. An issued
 * invoice can be voided (VOID) once nothing is paid on it; it keeps its number.
 */
public enum InvoiceStatus {
    DRAFT, ISSUED, PARTIALLY_PAID, PAID, VOID;

    /** Issued and not fully paid or voided: money is still owed. */
    public boolean isOpen() {
        return this == ISSUED || this == PARTIALLY_PAID;
    }
}
