package com.deroyqueit.productactivation.handler;

import com.deroyqueit.productactivation.domain.exception.ActivationNotFoundException;
import com.deroyqueit.productactivation.domain.exception.ExternalIdConflictException;
import com.deroyqueit.productactivation.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ActivationNotFoundException.class)
    ResponseEntity<Object> handleNotFound(ActivationNotFoundException ex) {
        return respond(problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage()));
    }

    @ExceptionHandler(ExternalIdConflictException.class)
    ResponseEntity<Object> handleConflict(ExternalIdConflictException ex) {
        return respond(problem(HttpStatus.CONFLICT, "external-id-conflict", "Conflicting request", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "An unexpected error occurred. Use the traceId to report it."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = ex.getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Invalid request data",
                "One or more fields failed validation.");
        problem.setProperty("errors", errors);
        return respond(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addTraceId(problem);
        }
        return response;
    }

    public static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + type));
        problem.setTitle(title);
        addTraceId(problem);
        return problem;
    }

    private static ResponseEntity<Object> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static void addTraceId(ProblemDetail problem) {
        problem.setProperty("traceId", MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private record FieldViolation(String field, String message) {
    }
}
