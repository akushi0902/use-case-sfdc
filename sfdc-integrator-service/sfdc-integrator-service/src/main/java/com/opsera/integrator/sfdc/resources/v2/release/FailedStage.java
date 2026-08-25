package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * Identifies which stage of the release workflow failed or was interrupted.
 *
 * <p>Used in {@link RollbackDecision#getFailedStage()} and derived
 * deterministically from checkpoint history and operation type.
 * Advisory only — does not trigger rollback execution.
 */
public enum FailedStage {

    /** Failure occurred during package XML validation before deployment. */
    PRE_VALIDATION,

    /** Failure occurred during the deployment apply phase. */
    DEPLOYMENT,

    /** Failure occurred during post-deployment verification or smoke tests. */
    POST_DEPLOYMENT,

    /** The workflow was cancelled before completion. */
    CANCELLATION,

    /** The workflow exceeded its execution time limit. */
    TIMEOUT,

    /** Stage could not be determined from available checkpoint data. */
    UNKNOWN
}
