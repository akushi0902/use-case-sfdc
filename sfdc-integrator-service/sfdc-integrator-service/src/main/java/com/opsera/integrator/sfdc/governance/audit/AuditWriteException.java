package com.opsera.integrator.sfdc.governance.audit;

/**
 * Thrown when an audit event cannot be persisted.
 *
 * <p>Carries the event ID, operation, and a safe failure category without
 * including stack traces, credentials, or raw payload fragments in its message.
 *
 * <p>Callers should let this exception propagate to the global exception handler
 * for governed mutation boundaries — audit failures must not be silently swallowed.
 */
public class AuditWriteException extends RuntimeException {

    private final String eventId;
    private final AuditOperation operation;
    private final String failureCategory;

    public AuditWriteException(String eventId, AuditOperation operation,
                                String failureCategory, Throwable cause) {
        super("Audit write failed: eventId=" + eventId + " operation=" + operation +
              " category=" + failureCategory, cause);
        this.eventId = eventId;
        this.operation = operation;
        this.failureCategory = failureCategory;
    }

    public String getEventId() { return eventId; }
    public AuditOperation getOperation() { return operation; }
    public String getFailureCategory() { return failureCategory; }
}
