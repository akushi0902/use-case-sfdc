package com.opsera.integrator.sfdc.exceptions;

/**
 * Thrown when an authenticated caller lacks the required service-level scope for an operation.
 *
 * <p>This exception carries only safe metadata suitable for structured logging and the error
 * response envelope. It never contains raw JWT claims, full token values, or request body
 * content.
 *
 * <p>Mapped to HTTP 403 by {@link SfdcExceptionHandler}.
 */
public class ScopeAuthorizationException extends RuntimeException {

    private final String requiredCapability;
    private final String denialReason;
    private final String correlationId;
    private final String safeActorRef;

    public ScopeAuthorizationException(String requiredCapability,
                                        String denialReason,
                                        String correlationId,
                                        String safeActorRef) {
        super("Authorization denied: required capability=" + requiredCapability
                + ", reason=" + denialReason);
        this.requiredCapability = requiredCapability;
        this.denialReason = denialReason;
        this.correlationId = correlationId;
        this.safeActorRef = safeActorRef;
    }

    /** The exact scope name the caller was missing. Safe to include in logs and responses. */
    public String getRequiredCapability() { return requiredCapability; }

    /** Short denial reason code (e.g. "INSUFFICIENT_SCOPE"). Safe to log. */
    public String getDenialReason() { return denialReason; }

    /** Correlation identifier from the caller's context or MDC. */
    public String getCorrelationId() { return correlationId; }

    /**
     * Safe reference to the actor — never a raw subject, client ID, or token value.
     * Typically a hash prefix or "unknown" when no identifying information is available.
     */
    public String getSafeActorRef() { return safeActorRef; }
}
