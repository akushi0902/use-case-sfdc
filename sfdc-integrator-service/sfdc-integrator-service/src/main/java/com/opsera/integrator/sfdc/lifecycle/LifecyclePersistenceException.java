package com.opsera.integrator.sfdc.lifecycle;

/**
 * Typed exception for lifecycle repository failures.
 *
 * <p>Messages must include the correlation context where available but must
 * never include credentials, raw payloads, bearer tokens, or stack trace
 * content from underlying datasource exceptions.
 */
public class LifecyclePersistenceException extends RuntimeException {

    public LifecyclePersistenceException(String message) {
        super(message);
    }

    public LifecyclePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
