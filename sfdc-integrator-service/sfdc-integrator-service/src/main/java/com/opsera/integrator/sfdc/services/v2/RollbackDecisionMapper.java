package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.resources.v2.release.FailedStage;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackDecision;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackReasonCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministically maps lifecycle state, operation type, and checkpoint history
 * to a {@link RollbackDecision} advisory object.
 *
 * <p>This component does NOT execute rollback — it only derives advisory fields.
 * All decision logic is pure and side-effect-free to make it easily testable.
 *
 * <p>Safe output guarantee: no raw logs, stack traces, Salesforce payloads,
 * credentials, or tokens appear in the returned DTO.
 */
@Component
public class RollbackDecisionMapper {

    static final int MAX_DIAGNOSTIC_LENGTH = 256;

    /**
     * Derives rollback decision fields from the job's terminal outcome.
     *
     * @param operationType operation type string (e.g. "DEPLOY", "VALIDATE", "QUICK_DEPLOY")
     * @param state         public lifecycle state
     * @param checkpoints   ordered checkpoint history; may be empty
     * @param now           evaluation timestamp (caller-supplied for determinism in tests)
     * @return populated advisory RollbackDecision; never null
     */
    public RollbackDecision map(String operationType, ReleaseLifecycleState state,
                                List<JobCheckpoint> checkpoints, Instant now) {
        RollbackDecision.Builder builder = RollbackDecision.builder().updatedAt(now);

        if (state == null) {
            return unknownDecision(builder, "Lifecycle state unavailable");
        }

        switch (state) {
            case COMPLETED:
                return builder
                        .rollbackEligible(false)
                        .rollbackRecommended(false)
                        .rollbackReasonCode(RollbackReasonCode.COMPLETED_SUCCESSFULLY)
                        .failedStage(null)
                        .lastSuccessfulCheckpoint(lastCheckpointCode(checkpoints))
                        .build();

            case FAILED:
                return mapFailedDecision(operationType, checkpoints, builder);

            case CANCELLED:
                return mapCancelledDecision(checkpoints, builder);

            case TIMED_OUT:
                return builder
                        .rollbackEligible(true)
                        .rollbackRecommended(true)
                        .rollbackReasonCode(RollbackReasonCode.TIMED_OUT_DURING_EXECUTION)
                        .failedStage(FailedStage.TIMEOUT)
                        .lastSuccessfulCheckpoint(lastCheckpointCode(checkpoints))
                        .confidenceNote("Timeout during execution — partial changes may have been applied")
                        .build();

            case ACCEPTED:
            case DISPATCHING:
            case RUNNING:
                return builder
                        .rollbackEligible(false)
                        .rollbackRecommended(false)
                        .rollbackReasonCode(RollbackReasonCode.UNKNOWN)
                        .failedStage(null)
                        .confidenceNote("Job has not reached a terminal state")
                        .build();

            default:
                return unknownDecision(builder, "Unrecognized lifecycle state");
        }
    }

    private RollbackDecision mapFailedDecision(String operationType,
                                               List<JobCheckpoint> checkpoints,
                                               RollbackDecision.Builder builder) {
        if (isValidationOnlyOperation(operationType)) {
            return builder
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.VALIDATION_ONLY_NO_DEPLOYMENT)
                    .failedStage(FailedStage.PRE_VALIDATION)
                    .lastSuccessfulCheckpoint(lastCheckpointCode(checkpoints))
                    .diagnosticSummary(buildSafeDiagnostic(checkpoints))
                    .build();
        }

        if (checkpoints.isEmpty()) {
            return builder
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.INSUFFICIENT_CHECKPOINT_DATA)
                    .failedStage(FailedStage.UNKNOWN)
                    .confidenceNote("No checkpoint history available to determine failure stage")
                    .build();
        }

        FailedStage stage = inferFailedStage(checkpoints);
        boolean eligibleForRollback = stage == FailedStage.DEPLOYMENT || stage == FailedStage.POST_DEPLOYMENT;

        return builder
                .rollbackEligible(eligibleForRollback)
                .rollbackRecommended(eligibleForRollback)
                .rollbackReasonCode(eligibleForRollback
                        ? RollbackReasonCode.DEPLOYMENT_PARTIAL_FAILURE
                        : RollbackReasonCode.VALIDATION_ONLY_NO_DEPLOYMENT)
                .failedStage(stage)
                .lastSuccessfulCheckpoint(lastCheckpointCode(checkpoints))
                .diagnosticSummary(buildSafeDiagnostic(checkpoints))
                .build();
    }

    private RollbackDecision mapCancelledDecision(List<JobCheckpoint> checkpoints,
                                                  RollbackDecision.Builder builder) {
        if (checkpoints.isEmpty()) {
            return builder
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.CANCELLED_BEFORE_APPLY)
                    .failedStage(FailedStage.CANCELLATION)
                    .confidenceNote("No checkpoint history — assuming cancellation before apply")
                    .build();
        }

        boolean changesApplied = checkpoints.stream()
                .anyMatch(cp -> cp.getCheckpointCode() != null
                        && cp.getCheckpointCode().contains("DEPLOY_APPLY"));

        return builder
                .rollbackEligible(changesApplied)
                .rollbackRecommended(changesApplied)
                .rollbackReasonCode(changesApplied
                        ? RollbackReasonCode.TIMED_OUT_DURING_EXECUTION
                        : RollbackReasonCode.CANCELLED_NO_CHANGES_APPLIED)
                .failedStage(FailedStage.CANCELLATION)
                .lastSuccessfulCheckpoint(lastCheckpointCode(checkpoints))
                .build();
    }

    private RollbackDecision unknownDecision(RollbackDecision.Builder builder, String note) {
        return builder
                .rollbackEligible(false)
                .rollbackRecommended(false)
                .rollbackReasonCode(RollbackReasonCode.UNKNOWN)
                .failedStage(FailedStage.UNKNOWN)
                .confidenceNote(note)
                .build();
    }

    /**
     * Infers the failed stage from the highest-sequence checkpoint codes.
     * Checkpoint codes containing "DEPLOY_APPLY" or "DEPLOY_COMMIT" indicate
     * the deployment phase was reached. "POST_DEPLOY" indicates post-deployment.
     * Anything else is treated as PRE_VALIDATION.
     */
    private FailedStage inferFailedStage(List<JobCheckpoint> checkpoints) {
        String lastCode = checkpoints.stream()
                .max(Comparator.comparingInt(JobCheckpoint::getSequenceNumber))
                .map(JobCheckpoint::getCheckpointCode)
                .orElse(null);

        if (lastCode == null) {
            return FailedStage.UNKNOWN;
        }
        String upper = lastCode.toUpperCase();
        if (upper.contains("POST_DEPLOY") || upper.contains("SMOKE_TEST") || upper.contains("VERIFY")) {
            return FailedStage.POST_DEPLOYMENT;
        }
        if (upper.contains("DEPLOY") || upper.contains("APPLY") || upper.contains("COMMIT")) {
            return FailedStage.DEPLOYMENT;
        }
        if (upper.contains("VALIDATE") || upper.contains("CHECK") || upper.contains("PRE")) {
            return FailedStage.PRE_VALIDATION;
        }
        return FailedStage.UNKNOWN;
    }

    private String lastCheckpointCode(List<JobCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return null;
        }
        return checkpoints.stream()
                .max(Comparator.comparingInt(JobCheckpoint::getSequenceNumber))
                .map(JobCheckpoint::getCheckpointCode)
                .orElse(null);
    }

    private String buildSafeDiagnostic(List<JobCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return null;
        }
        String msg = checkpoints.stream()
                .max(Comparator.comparingInt(JobCheckpoint::getSequenceNumber))
                .map(JobCheckpoint::getSafeMessage)
                .orElse(null);
        if (msg == null) {
            return null;
        }
        if (msg.length() > MAX_DIAGNOSTIC_LENGTH) {
            return msg.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
        }
        return msg;
    }

    private boolean isValidationOnlyOperation(String operationType) {
        if (operationType == null) return false;
        String upper = operationType.toUpperCase();
        return upper.contains("VALIDATE") || upper.equals("VALIDATE_ONLY");
    }
}
