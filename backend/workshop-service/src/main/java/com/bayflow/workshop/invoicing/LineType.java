package com.bayflow.workshop.invoicing;

/** PART and LABOUR come from the job card; FEE and DISCOUNT are added on the draft. */
public enum LineType {
    PART, LABOUR, FEE, DISCOUNT;

    /** Lines that come from the job card: repriced on a draft, never removed. */
    public boolean fromJobCard() {
        return this == PART || this == LABOUR;
    }
}
