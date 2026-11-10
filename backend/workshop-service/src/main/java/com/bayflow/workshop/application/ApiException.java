package com.bayflow.workshop.application;

import org.springframework.http.HttpStatus;

/**
 * An error with an HTTP status and a stable problem type, rendered as RFC 7807
 * Problem Details by the API layer.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    public ApiException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public static ApiException conflict(String type, String detail) {
        return new ApiException(HttpStatus.CONFLICT, type, "Conflict", detail);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Not Found", what + " was not found.");
    }

    public static ApiException badRequest(String type, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, type, "Bad Request", detail);
    }

    public HttpStatus getStatus() { return status; }
    public String getType() { return type; }
    public String getTitle() { return title; }
}
