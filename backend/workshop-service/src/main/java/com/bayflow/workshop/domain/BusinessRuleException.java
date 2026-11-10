package com.bayflow.workshop.domain;

/** A domain rule was broken (rendered as 409 Conflict with the given problem type). */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
