package com.bayflow.workshop.domain;

import static com.bayflow.workshop.domain.JobCardStatus.APPROVED;
import static com.bayflow.workshop.domain.JobCardStatus.AWAITING_APPROVAL;
import static com.bayflow.workshop.domain.JobCardStatus.DECLINED;
import static com.bayflow.workshop.domain.JobCardStatus.INSPECTED;
import static com.bayflow.workshop.domain.JobCardStatus.IN_PROGRESS;
import static com.bayflow.workshop.domain.JobCardStatus.OPEN;
import static com.bayflow.workshop.domain.JobCardStatus.PARTIALLY_APPROVED;

import java.util.EnumSet;
import java.util.Set;

/**
 * What can happen to a job card and the statuses it is allowed from. This table is the
 * job card state machine; anything not listed is rejected.
 */
public enum JobCardAction {
    ADD_FINDING("add a finding to", EnumSet.of(OPEN, INSPECTED)),
    ADD_RECOMMENDATION("add a recommendation to", EnumSet.of(OPEN, INSPECTED)),
    SUBMIT_FOR_APPROVAL("submit for approval", EnumSet.of(INSPECTED)),
    DECIDE_ITEM("approve or decline work on", EnumSet.of(AWAITING_APPROVAL)),
    START("start work on", EnumSet.of(APPROVED, PARTIALLY_APPROVED)),
    /** Work logs, parts and labour. */
    RECORD_WORK("record work on", EnumSet.of(IN_PROGRESS)),
    /** DECLINED cards complete as inspection only. */
    COMPLETE("complete", EnumSet.of(IN_PROGRESS, DECLINED));

    private final String description;
    private final Set<JobCardStatus> allowedFrom;

    JobCardAction(String description, Set<JobCardStatus> allowedFrom) {
        this.description = description;
        this.allowedFrom = allowedFrom;
    }

    public boolean isAllowedFrom(JobCardStatus status) {
        return allowedFrom.contains(status);
    }

    public String description() {
        return description;
    }
}
