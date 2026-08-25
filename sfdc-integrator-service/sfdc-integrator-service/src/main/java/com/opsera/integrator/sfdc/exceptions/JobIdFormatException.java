package com.opsera.integrator.sfdc.exceptions;

/**
 * Thrown when a job ID path variable fails format validation before querying any status source.
 *
 * <p>Results in HTTP 400 from {@link SfdcExceptionHandler}. The rejection reason is safe
 * (no raw rejected value) and only the field name is included in the structured error response.
 */
public class JobIdFormatException extends RuntimeException {

    private final String correlationId;
    private final String rejectionReason;

    public JobIdFormatException(String correlationId, String rejectionReason) {
        super("Job ID format validation failed: " + rejectionReason);
        this.correlationId = correlationId;
        this.rejectionReason = rejectionReason;
    }

    public String getCorrelationId() { return correlationId; }
    public String getRejectionReason() { return rejectionReason; }
}
