package com.bayflow.workshop.domain;

public enum JobCardStatus {
    OPEN,
    INSPECTED,
    AWAITING_APPROVAL,
    APPROVED,
    PARTIALLY_APPROVED,
    DECLINED,
    /** Approved work under way. */
    IN_PROGRESS,
    /** Finished; totals calculated and service history recorded. */
    COMPLETED
}
