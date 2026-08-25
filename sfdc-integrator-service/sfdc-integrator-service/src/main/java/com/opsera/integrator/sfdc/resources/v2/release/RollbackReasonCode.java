package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * Reason codes that explain why a rollback is or is not recommended.
 *
 * <p>Used in {@link RollbackDecision#getRollbackReasonCode()} and derived
 * deterministically from operation type, lifecycle state, and checkpoint history.
 * Advisory only — does not trigger rollback execution.
 */
public enum RollbackReasonCode {

    /** Job failed during deployment after changes were partially applied. */
    DEPLOYMENT_PARTIAL_FAILURE,

    /** Validation-only job — no deployment occurred; rollback is not applicable. */
    VALIDATION_ONLY_NO_DEPLOYMENT,

    /** Job was cancelled before any deployment apply step ran. */
    CANCELLED_BEFORE_APPLY,

    /** Job was cancelled and checkpoints confirm no changes were applied. */
    CANCELLED_NO_CHANGES_APPLIED,

    /** Job timed out while executing; partial changes may have been applied. */
    TIMED_OUT_DURING_EXECUTION,

    /** Job completed successfully; no rollback needed. */
    COMPLETED_SUCCESSFULLY,

    /** Checkpoint history is absent or insufficient to determine rollback eligibility. */
    INSUFFICIENT_CHECKPOINT_DATA,

    /** Rollback eligibility could not be determined from available data. */
    UNKNOWN
}
