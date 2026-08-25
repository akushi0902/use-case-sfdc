package com.opsera.integrator.sfdc.governance.retention;

/**
 * Thrown when retention metadata cannot be assigned for a governed record.
 *
 * <p>Error messages include only the correlation identifier and failure category —
 * no raw payload, org URL, credentials, or customer data.
 *
 * <p>This is a fatal error: governed data must not be created without retention metadata.
 * Callers must propagate this exception rather than swallowing it.
 */
public class RetentionAssignmentException extends RuntimeException {

    private final String correlationId;
    private final String failureCategory;

    public RetentionAssignmentException(String message, String correlationId,
                                        String failureCategory, Throwable cause) {
        super(message, cause);
        this.correlationId = correlationId;
        this.failureCategory = failureCategory;
    }

    public RetentionAssignmentException(String message, String correlationId,
                                        String failureCategory) {
        super(message);
        this.correlationId = correlationId;
        this.failureCategory = failureCategory;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getFailureCategory() {
        return failureCategory;
    }
}
