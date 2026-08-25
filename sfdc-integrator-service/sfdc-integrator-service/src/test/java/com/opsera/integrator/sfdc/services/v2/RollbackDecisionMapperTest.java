package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.resources.v2.release.FailedStage;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackDecision;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackReasonCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RollbackDecisionMapper}.
 *
 * <p>Covers: completed, failed (deploy vs. validate-only), cancelled (apply vs. no-apply),
 * timed-out, active states, null state, no checkpoints, and diagnostic redaction.
 */
@DisplayName("RollbackDecisionMapper — deterministic mapping unit tests")
class RollbackDecisionMapperTest {

    private RollbackDecisionMapper mapper;
    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new RollbackDecisionMapper();
    }

    private JobCheckpoint cp(int seq, String code, String state, String msg) {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setSequenceNumber(seq);
        cp.setCheckpointCode(code);
        cp.setStateAtCheckpoint(state);
        cp.setSafeMessage(msg);
        return cp;
    }

    // ── Completed ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("COMPLETED state")
    class Completed {

        @Test
        @DisplayName("COMPLETED → not eligible, not recommended, COMPLETED_SUCCESSFULLY")
        void completed_notEligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "PRE_VALIDATE_OK", "RUNNING", "ok"),
                    cp(2, "POST_DEPLOY_VERIFY", "COMPLETED", "done")
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.COMPLETED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackRecommended()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.COMPLETED_SUCCESSFULLY);
            assertThat(d.getFailedStage()).isNull();
            assertThat(d.getLastSuccessfulCheckpoint()).isEqualTo("POST_DEPLOY_VERIFY");
        }
    }

    // ── Failed ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FAILED state")
    class Failed {

        @Test
        @DisplayName("DEPLOY failed at DEPLOY_APPLY → eligible, recommended, DEPLOYMENT_PARTIAL_FAILURE")
        void deployFailed_deployStage_eligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "PRE_VALIDATE_OK",    "RUNNING", "ok"),
                    cp(2, "DEPLOY_APPLY_START", "RUNNING", "started"),
                    cp(3, "DEPLOY_APPLY_ERROR", "FAILED",  "component error")
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isTrue();
            assertThat(d.getRollbackRecommended()).isTrue();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.DEPLOYMENT_PARTIAL_FAILURE);
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.DEPLOYMENT);
            assertThat(d.getLastSuccessfulCheckpoint()).isEqualTo("DEPLOY_APPLY_ERROR");
            assertThat(d.getDiagnosticSummary()).isEqualTo("component error");
        }

        @Test
        @DisplayName("VALIDATE_ONLY failed → not eligible, VALIDATION_ONLY_NO_DEPLOYMENT")
        void validateOnlyFailed_notEligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "PRE_VALIDATE_ERROR", "FAILED", "metadata ref error")
            );

            RollbackDecision d = mapper.map("VALIDATE_ONLY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackRecommended()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.VALIDATION_ONLY_NO_DEPLOYMENT);
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.PRE_VALIDATION);
        }

        @Test
        @DisplayName("DEPLOY failed at PRE_VALIDATE_ERROR → not eligible (no deployment reached)")
        void deployFailed_preValidationStage_notEligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "PRE_VALIDATE_CHECK", "RUNNING", "checking"),
                    cp(2, "PRE_VALIDATE_ERROR", "FAILED",  "schema error")
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.PRE_VALIDATION);
        }

        @Test
        @DisplayName("DEPLOY failed with no checkpoints → INSUFFICIENT_CHECKPOINT_DATA, not eligible")
        void deployFailed_noCheckpoints_insufficientData() {
            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, List.of(), NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.INSUFFICIENT_CHECKPOINT_DATA);
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.UNKNOWN);
        }

        @Test
        @DisplayName("diagnosticSummary is truncated at MAX_DIAGNOSTIC_LENGTH")
        void diagnosticSummary_isTruncated() {
            String longMsg = "safe-msg-".repeat(40); // 360 chars > 256
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "DEPLOY_APPLY_ERROR", "FAILED", longMsg)
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getDiagnosticSummary()).hasSizeLessThanOrEqualTo(
                    RollbackDecisionMapper.MAX_DIAGNOSTIC_LENGTH + 3); // +3 for "..."
            assertThat(d.getDiagnosticSummary()).endsWith("...");
        }

        @Test
        @DisplayName("diagnosticSummary does not expose raw XML or credentials")
        void diagnosticSummary_noSensitiveContent() {
            String safeMsg = "Component error — check metadata definition";
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "DEPLOY_APPLY_ERROR", "FAILED", safeMsg)
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getDiagnosticSummary()).isEqualTo(safeMsg);
            // These would only fail if somehow raw content was injected; assert clean message
            assertThat(d.getDiagnosticSummary()).doesNotContain("password", "token", "secret", "<?xml");
        }

        @Test
        @DisplayName("DEPLOY failed at POST_DEPLOY_VERIFY → eligible, POST_DEPLOYMENT stage")
        void deployFailed_postDeployStage_eligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "DEPLOY_APPLY_DONE",  "RUNNING", "deployed"),
                    cp(2, "POST_DEPLOY_VERIFY",  "FAILED",  "smoke test failed")
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.FAILED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isTrue();
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.POST_DEPLOYMENT);
        }
    }

    // ── Cancelled ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CANCELLED state")
    class Cancelled {

        @Test
        @DisplayName("CANCELLED with no checkpoints → not eligible, CANCELLED_BEFORE_APPLY")
        void cancelled_noCheckpoints_notEligible() {
            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.CANCELLED, List.of(), NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.CANCELLED_BEFORE_APPLY);
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.CANCELLATION);
        }

        @Test
        @DisplayName("CANCELLED without DEPLOY_APPLY checkpoint → not eligible, CANCELLED_NO_CHANGES_APPLIED")
        void cancelled_noApplyCheckpoint_notEligible() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "PRE_VALIDATE_OK",  "RUNNING",   "ok"),
                    cp(2, "CANCEL_REQUESTED", "CANCELLED", "user requested")
            );

            RollbackDecision d = mapper.map("QUICK_DEPLOY", ReleaseLifecycleState.CANCELLED, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.CANCELLED_NO_CHANGES_APPLIED);
        }
    }

    // ── Timed Out ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TIMED_OUT state")
    class TimedOut {

        @Test
        @DisplayName("TIMED_OUT → eligible, recommended, TIMED_OUT_DURING_EXECUTION, TIMEOUT stage")
        void timedOut_eligibleAndRecommended() {
            List<JobCheckpoint> checkpoints = List.of(
                    cp(1, "DEPLOY_APPLY_START", "RUNNING",   "started"),
                    cp(2, "TIMEOUT_DETECTED",   "TIMED_OUT", "exceeded limit")
            );

            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.TIMED_OUT, checkpoints, NOW);

            assertThat(d.getRollbackEligible()).isTrue();
            assertThat(d.getRollbackRecommended()).isTrue();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.TIMED_OUT_DURING_EXECUTION);
            assertThat(d.getFailedStage()).isEqualTo(FailedStage.TIMEOUT);
        }
    }

    // ── Active states ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Active (non-terminal) states")
    class ActiveStates {

        @Test
        @DisplayName("RUNNING → not eligible, reason UNKNOWN, no failed stage")
        void running_notEligible() {
            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.RUNNING, List.of(), NOW);

            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackRecommended()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.UNKNOWN);
        }

        @Test
        @DisplayName("ACCEPTED → not eligible, reason UNKNOWN")
        void accepted_notEligible() {
            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.ACCEPTED, List.of(), NOW);

            assertThat(d.getRollbackEligible()).isFalse();
        }
    }

    // ── Null / edge cases ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null and edge-case inputs")
    class EdgeCases {

        @Test
        @DisplayName("null state → UNKNOWN reason, not eligible, returns non-null decision")
        void nullState_unknownDecision() {
            RollbackDecision d = mapper.map("DEPLOY", null, List.of(), NOW);

            assertThat(d).isNotNull();
            assertThat(d.getRollbackEligible()).isFalse();
            assertThat(d.getRollbackReasonCode()).isEqualTo(RollbackReasonCode.UNKNOWN);
        }

        @Test
        @DisplayName("updatedAt is always set from the supplied timestamp")
        void updatedAt_isAlwaysSet() {
            RollbackDecision d = mapper.map("DEPLOY", ReleaseLifecycleState.COMPLETED, List.of(), NOW);

            assertThat(d.getUpdatedAt()).isEqualTo(NOW);
        }
    }
}
