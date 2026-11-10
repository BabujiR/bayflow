package com.bayflow.appointment.domain;

/** A domain rule was broken, e.g. marking a no-show before the slot started (rendered as 409 Conflict). */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
