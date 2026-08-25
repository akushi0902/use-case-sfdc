package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;

/**
 * Value object carrying the lifecycle state transition implied by a checkpoint event.
 *
 * <p>The {@link CheckpointPersistenceAdapter} applies this transition via
 * {@code JobLifecycleService} after persisting the checkpoint record.
 */
public final class CheckpointStateTransition {

    private final JobLifecycleState targetState;
    private final String safeReasonCode;
    private final String safeDiagnosticSummary;

    public CheckpointStateTransition(JobLifecycleState targetState,
                                     String safeReasonCode,
                                     String safeDiagnosticSummary) {
        this.targetState = targetState;
        this.safeReasonCode = safeReasonCode;
        this.safeDiagnosticSummary = safeDiagnosticSummary;
    }

    public JobLifecycleState getTargetState() { return targetState; }
    public String getSafeReasonCode() { return safeReasonCode; }
    public String getSafeDiagnosticSummary() { return safeDiagnosticSummary; }

    @Override
    public String toString() {
        return "CheckpointStateTransition{target=" + targetState
                + ", reasonCode='" + safeReasonCode + "'}";
    }
}
