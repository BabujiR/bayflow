package com.bayflow.auth.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.TooManyRequestsException;

/** Renders every API error as RFC 7807 Problem Details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    static final String TYPE_PREFIX = "urn:bayflow:problem:";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex instanceof TooManyRequestsException tooMany) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, tooMany.getRetryAfter().getSeconds())));
        }
        return builder.body(problem(ex.getStatus(), ex.getType(), ex.getTitle(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldError)
                .toList();
        ProblemDetail problem = new ValidationProblemDetail(errors);
        problem.setType(URI.create(TYPE_PREFIX + "validation-failed"));
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ProblemDetail> handleUnreadable(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Bad Request", "The request could not be read. Check the JSON body and parameters."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this action."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, problem(HttpStatus.CONFLICT, "concurrent-update", "Conflict",
                "The record was changed by someone else. Reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation", ex);
        return respond(HttpStatus.CONFLICT, problem(HttpStatus.CONFLICT, "conflict", "Conflict",
                "The request conflicts with existing data."));
    }

    static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }
}
