package com.bayflow.appointment.domain;

/** An action is not allowed in the record's current status (rendered as 409 Conflict). */
public class InvalidTransitionException extends RuntimeException {

    private final String currentStatus;
    private final String action;

    public InvalidTransitionException(String entity, Enum<?> currentStatus, String action) {
        super("Cannot " + action + " a " + entity + " that is " + currentStatus + ".");
        this.currentStatus = currentStatus.name();
        this.action = action;
    }

    public String getCurrentStatus() { return currentStatus; }
    public String getAction() { return action; }
}
