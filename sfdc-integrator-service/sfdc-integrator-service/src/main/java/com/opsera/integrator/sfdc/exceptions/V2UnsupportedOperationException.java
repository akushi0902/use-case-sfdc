package com.opsera.integrator.sfdc.exceptions;

/**
 * Thrown when a v2 release command requests an operation type that is not yet
 * supported by the current implementation.
 *
 * <p>The exception handler maps this to HTTP 422 with a structured error response.
 * The {@code operationType} field carries the safe string name of the unsupported
 * operation — it must never contain raw payload values.
 */
public class V2UnsupportedOperationException extends RuntimeException {

    private final String operationType;

    public V2UnsupportedOperationException(String operationType) {
        super("Operation type " + operationType + " is not supported");
        this.operationType = operationType;
    }

    public String getOperationType() {
        return operationType;
    }
}
