package com.bayflow.workshop.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ProblemDetail;

/** Problem Details for a 400 validation failure, with one entry per invalid field. */
public class ValidationProblemDetail extends ProblemDetail {

    private final List<Map<String, String>> errors;

    public ValidationProblemDetail(List<Map<String, String>> errors) {
        super(400);
        this.errors = List.copyOf(errors);
    }

    public List<Map<String, String>> getErrors() {
        return errors;
    }
}
