package com.bayflow.workshop.domain;

/** An action is not allowed in the record's current status (rendered as 409 Conflict). */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String entity, Enum<?> currentStatus, String action) {
        super("Cannot " + action + " a " + entity + " that is " + currentStatus + ".");
    }
}
