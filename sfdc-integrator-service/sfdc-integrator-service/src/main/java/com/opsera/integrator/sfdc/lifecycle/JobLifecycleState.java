package com.opsera.integrator.sfdc.lifecycle;

import java.util.Set;

/**
 * Canonical lifecycle state for durable release jobs.
 *
 * <p>Terminal states ({@link #COMPLETED}, {@link #FAILED}, {@link #CANCELLED},
 * {@link #TIMED_OUT}, {@link #REJECTED}) must not be overwritten by late worker
 * or checkpoint events. All valid transitions are enforced by
 * {@link LifecycleTransitionPolicy}.
 */
public enum JobLifecycleState {

    /** Command accepted and persisted; no worker dispatched yet. */
    ACCEPTED,

    /** Worker dispatch in progress; handoff to execution layer pending. */
    DISPATCHING,

    /** Worker actively executing the release operation. */
    RUNNING,

    /** Release operation completed successfully. Terminal. */
    COMPLETED,

    /** Release operation failed; safe diagnostic available. Terminal. */
    FAILED,

    /** Job cancelled before or during execution. Terminal. */
    CANCELLED,

    /** Job exceeded its execution time limit without completing. Terminal. */
    TIMED_OUT,

    /** Job rejected before dispatch due to a policy or validation failure. Terminal. */
    REJECTED;

    private static final Set<JobLifecycleState> TERMINAL_STATES =
            Set.of(COMPLETED, FAILED, CANCELLED, TIMED_OUT, REJECTED);

    private static final Set<JobLifecycleState> ACTIVE_STATES =
            Set.of(ACCEPTED, DISPATCHING, RUNNING);

    /**
     * Returns {@code true} if this state is terminal and must not be followed by
     * any further lifecycle transition.
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * Returns {@code true} if this state represents an in-progress job that has
     * not yet reached a terminal outcome.
     */
    public boolean isActive() {
        return ACTIVE_STATES.contains(this);
    }

    /**
     * Parses a state string (case-insensitive) into a {@link JobLifecycleState}.
     *
     * @throws LifecycleTransitionException if the value is unknown
     */
    public static JobLifecycleState fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new LifecycleTransitionException("Lifecycle state value must not be null or blank");
        }
        try {
            return JobLifecycleState.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LifecycleTransitionException("Unknown lifecycle state: '" + value + "'");
        }
    }
}
