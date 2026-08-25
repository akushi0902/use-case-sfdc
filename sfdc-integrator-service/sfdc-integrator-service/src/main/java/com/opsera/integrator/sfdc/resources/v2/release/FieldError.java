package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * A single field-level validation error for inclusion in {@link V2ErrorResponse}.
 * {@code rejectedValue} must never contain secrets, tokens, or raw Salesforce payloads.
 */
public class FieldError {

    private final String field;
    private final String rejectedValue;
    private final String message;

    public FieldError(String field, String rejectedValue, String message) {
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.message = message;
    }

    public String getField() { return field; }
    public String getRejectedValue() { return rejectedValue; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "FieldError{field='" + field + "', message='" + message + "'}";
    }
}
