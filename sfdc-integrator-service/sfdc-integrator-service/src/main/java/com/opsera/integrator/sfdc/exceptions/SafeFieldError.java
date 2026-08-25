package com.opsera.integrator.sfdc.exceptions;

/**
 * Boundary-safe representation of a single field-level validation failure.
 *
 * <p>Intentionally omits the raw rejected value so that sensitive, payload-like,
 * or token-like input is never echoed back to callers in error responses.
 */
public class SafeFieldError {

    private String field;
    private String rejectedReason;
    private String safeMessage;

    public SafeFieldError() {}

    public SafeFieldError(String field, String rejectedReason, String safeMessage) {
        this.field = field;
        this.rejectedReason = rejectedReason;
        this.safeMessage = safeMessage;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }
}
