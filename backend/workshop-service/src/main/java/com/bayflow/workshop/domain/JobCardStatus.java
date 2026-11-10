package com.bayflow.workshop.domain;

public enum JobCardStatus {
    OPEN,
    INSPECTED,
    AWAITING_APPROVAL,
    APPROVED,
    PARTIALLY_APPROVED,
    DECLINED,
    /** Milestone 5: work under way. */
    IN_PROGRESS,
    /** Milestone 5: work finished, history recorded. */
    COMPLETED
}
