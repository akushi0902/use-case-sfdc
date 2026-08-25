package com.opsera.integrator.sfdc.exceptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured error response envelope for boundary validation and request parsing failures.
 *
 * <p>Returned by {@link SfdcExceptionHandler} for all client-side (4xx) and
 * unexpected (5xx) error conditions. Never includes raw rejected values, stack
 * traces, or internal exception details.
 */
public class ErrorResponse {

    private String correlationId;
    private String errorCode;
    private String message;
    private String remediation;
    private String timestamp;
    private List<SafeFieldError> fieldErrors;

    public ErrorResponse() {
        this.fieldErrors = new ArrayList<>();
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(String correlationId, String errorCode, String message, String remediation) {
        this();
        this.correlationId = correlationId;
        this.errorCode = errorCode;
        this.message = message;
        this.remediation = remediation;
    }

    public void addFieldError(SafeFieldError fieldError) {
        this.fieldErrors.add(fieldError);
    }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRemediation() { return remediation; }
    public void setRemediation(String remediation) { this.remediation = remediation; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<SafeFieldError> getFieldErrors() {
        return Collections.unmodifiableList(fieldErrors);
    }

    public void setFieldErrors(List<SafeFieldError> fieldErrors) {
        this.fieldErrors = new ArrayList<>(fieldErrors);
    }
}
