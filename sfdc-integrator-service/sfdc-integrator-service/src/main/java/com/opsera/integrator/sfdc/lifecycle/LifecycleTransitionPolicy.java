package com.opsera.integrator.sfdc.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the allowed lifecycle state transition matrix for release jobs.
 *
 * <p>This is pure domain logic with no Spring dependencies — it can be instantiated
 * directly in unit tests without a Spring context.
 *
 * <h3>Transition matrix</h3>
 * <pre>
 *   ACCEPTED    → DISPATCHING, RUNNING, FAILED, CANCELLED, REJECTED
 *   DISPATCHING → RUNNING, FAILED, CANCELLED, TIMED_OUT
 *   RUNNING     → COMPLETED, FAILED, CANCELLED, TIMED_OUT
 *   COMPLETED   → (terminal — no transitions)
 *   FAILED      → (terminal — no transitions)
 *   CANCELLED   → (terminal — no transitions)
 *   TIMED_OUT   → (terminal — no transitions)
 *   REJECTED    → (terminal — no transitions)
 * </pre>
 *
 * <p>Idempotent replay: if {@code from == to} the caller should handle it as a
 * no-op rather than invoking this policy — see {@link JobLifecycleService}.
 */
public class LifecycleTransitionPolicy {

    private static final Map<JobLifecycleState, Set<JobLifecycleState>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(JobLifecycleState.class);
        ALLOWED.put(JobLifecycleState.ACCEPTED,    EnumSet.of(
                JobLifecycleState.DISPATCHING,
                JobLifecycleState.RUNNING,
                JobLifecycleState.FAILED,
                JobLifecycleState.CANCELLED,
                JobLifecycleState.REJECTED));
        ALLOWED.put(JobLifecycleState.DISPATCHING, EnumSet.of(
                JobLifecycleState.RUNNING,
                JobLifecycleState.FAILED,
                JobLifecycleState.CANCELLED,
                JobLifecycleState.TIMED_OUT));
        ALLOWED.put(JobLifecycleState.RUNNING,     EnumSet.of(
                JobLifecycleState.COMPLETED,
                JobLifecycleState.FAILED,
                JobLifecycleState.CANCELLED,
                JobLifecycleState.TIMED_OUT));
        // Terminal states: no allowed transitions
        for (JobLifecycleState terminal : new JobLifecycleState[]{
                JobLifecycleState.COMPLETED,
                JobLifecycleState.FAILED,
                JobLifecycleState.CANCELLED,
                JobLifecycleState.TIMED_OUT,
                JobLifecycleState.REJECTED}) {
            ALLOWED.put(terminal, EnumSet.noneOf(JobLifecycleState.class));
        }
    }

    /**
     * Validates that a transition from {@code from} to {@code to} is permitted.
     *
     * @throws LifecycleTransitionException if the transition is not in the allowed matrix
     */
    public void validate(JobLifecycleState from, JobLifecycleState to) {
        if (from == null) {
            throw new LifecycleTransitionException("Source state must not be null");
        }
        if (to == null) {
            throw new LifecycleTransitionException("Target state must not be null");
        }
        Set<JobLifecycleState> allowed = ALLOWED.getOrDefault(from, EnumSet.noneOf(JobLifecycleState.class));
        if (!allowed.contains(to)) {
            throw new LifecycleTransitionException(from.name(), to.name(),
                    from.isTerminal()
                            ? "source state is terminal and cannot be transitioned further"
                            : "transition is not in the allowed matrix");
        }
    }

    /**
     * Returns {@code true} if the transition from {@code from} to {@code to} is permitted.
     */
    public boolean isAllowed(JobLifecycleState from, JobLifecycleState to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(JobLifecycleState.class)).contains(to);
    }

    /**
     * Returns the set of states reachable from the given state.
     * Returns an empty set for terminal states.
     */
    public Set<JobLifecycleState> allowedFrom(JobLifecycleState from) {
        if (from == null) {
            return EnumSet.noneOf(JobLifecycleState.class);
        }
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(JobLifecycleState.class)));
    }
}
