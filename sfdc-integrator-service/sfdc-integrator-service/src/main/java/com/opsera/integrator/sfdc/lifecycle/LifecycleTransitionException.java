package com.opsera.integrator.sfdc.lifecycle;

/**
 * Thrown when a requested lifecycle state transition is not permitted by
 * {@link LifecycleTransitionPolicy} or when input to the state machine is invalid.
 *
 * <p>Messages must describe the invalid transition by state names only and must
 * never include raw request payloads, credentials, or stack trace content.
 */
public class LifecycleTransitionException extends RuntimeException {

    private final String fromState;
    private final String toState;

    public LifecycleTransitionException(String message) {
        super(message);
        this.fromState = null;
        this.toState = null;
    }

    public LifecycleTransitionException(String fromState, String toState, String reason) {
        super("Transition from " + fromState + " to " + toState + " is not permitted: " + reason);
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
}
