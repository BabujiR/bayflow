package com.bayflow.workshop.domain;

import static com.bayflow.workshop.domain.JobCardStatus.AWAITING_APPROVAL;
import static com.bayflow.workshop.domain.JobCardStatus.INSPECTED;
import static com.bayflow.workshop.domain.JobCardStatus.OPEN;

import java.util.EnumSet;
import java.util.Set;

/**
 * What can happen to a job card and the statuses it is allowed from: the job card state
 * machine up to the customer's decision. Work and completion are added in milestone 5.
 */
public enum JobCardAction {
    ADD_FINDING("add a finding to", EnumSet.of(OPEN, INSPECTED)),
    ADD_RECOMMENDATION("add a recommendation to", EnumSet.of(OPEN, INSPECTED)),
    SUBMIT_FOR_APPROVAL("submit for approval", EnumSet.of(INSPECTED)),
    DECIDE_ITEM("approve or decline work on", EnumSet.of(AWAITING_APPROVAL));

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
