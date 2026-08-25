package com.opsera.integrator.sfdc.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Shared exception handler for legacy SFDC Integrator controllers.
 *
 * <p>Handles deserialization failures (malformed request body) and unhandled
 * service exceptions. Returns safe string messages without leaking internal
 * stack traces or credentials.
 *
 * <p>Note: current behavior returns plain string bodies; modernization to a
 * structured error envelope is deferred to a later story.
 */
@ControllerAdvice
public class SfdcExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SfdcExceptionHandler.class);

    /**
     * Returns 400 when the request body cannot be parsed as the expected DTO.
     * Observed behavior: malformed JSON produces this response without exposing
     * parser internals to the caller.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedRequestBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body("Malformed request body");
    }

    /**
     * Returns 500 for unhandled service exceptions.
     * Risk note (characterization): this catch-all may obscure root causes;
     * safe error response modernization is tracked for a later story.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal service error");
    }
}
