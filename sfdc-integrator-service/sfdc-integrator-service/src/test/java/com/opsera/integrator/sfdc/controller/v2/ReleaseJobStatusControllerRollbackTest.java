package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.release.FailedStage;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackDecision;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackReasonCode;
import com.opsera.integrator.sfdc.services.v2.JobStatusAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests verifying rollback decision fields are serialized correctly
 * in the v2 status endpoint response for success, failure, timeout, cancellation,
 * and unknown-data cases.
 *
 * <p>Pairs with {@link ReleaseJobStatusControllerTest} for base endpoint coverage.
 * Tests only the rollback decision contract — uses MockMvc + @WebMvcTest.
 */
@WebMvcTest(ReleaseJobStatusController.class)
@Import(SfdcExceptionHandler.class)
@WithMockUser
@DisplayName("ReleaseJobStatusController — rollback decision field contract tests")
class ReleaseJobStatusControllerRollbackTest {

    private static final String STATUS_URL = "/api/v2/sfdc/release-jobs/{jobId}/status";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobStatusAdapter statusAdapter;

    @MockBean
    private SafeStructuredLogger safeLogger;

    @MockBean
    private ReleaseCoexistenceTelemetry telemetry;

    private ReleaseJobStatusResponse withRollbackDecision(String jobId, ReleaseLifecycleState state,
                                                          RollbackDecision decision) {
        ReleaseJobStatusResponse r = new ReleaseJobStatusResponse();
        r.setJobId(jobId);
        r.setCorrelationId("corr-rd-ctrl-001");
        r.setOperationType("DEPLOY");
        r.setState(state);
        r.setAcceptedAt(Instant.parse("2024-01-01T10:00:00Z"));
        r.setUpdatedAt(Instant.parse("2024-01-01T10:05:00Z"));
        r.setCheckpoints(List.of());
        r.setSafeWarnings(List.of());
        r.setStatusSource("DURABLE_LIFECYCLE");
        r.setRollbackDecision(decision);
        return r;
    }

    // ── Completed deploy ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Completed deployment — rollback not recommended")
    class CompletedDeploy {

        @Test
        @DisplayName("COMPLETED → rollbackEligible=false, rollbackRecommended=false in response")
        void completed_rollbackNotRecommended() throws Exception {
            String jobId = "rd-ctrl-completed-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.COMPLETED_SUCCESSFULLY)
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.COMPLETED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(false))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackRecommended").value(false))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("COMPLETED_SUCCESSFULLY"));
        }
    }

    // ── Failed deployment ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Failed deployment — rollback recommended")
    class FailedDeploy {

        @Test
        @DisplayName("FAILED deploy → rollbackEligible=true, failedStage=DEPLOYMENT in response")
        void failed_deploy_rollbackRecommended() throws Exception {
            String jobId = "rd-ctrl-failed-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(true)
                    .rollbackRecommended(true)
                    .rollbackReasonCode(RollbackReasonCode.DEPLOYMENT_PARTIAL_FAILURE)
                    .failedStage(FailedStage.DEPLOYMENT)
                    .lastSuccessfulCheckpoint("DEPLOY_APPLY_ERROR")
                    .diagnosticSummary("Component error — partial changes applied")
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.FAILED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(true))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackRecommended").value(true))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("DEPLOYMENT_PARTIAL_FAILURE"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("DEPLOYMENT"))
                    .andExpect(jsonPath("$.rollbackDecision.lastSuccessfulCheckpoint").value("DEPLOY_APPLY_ERROR"))
                    .andExpect(jsonPath("$.rollbackDecision.diagnosticSummary").value("Component error — partial changes applied"));
        }

        @Test
        @DisplayName("FAILED validate-only → rollbackEligible=false, VALIDATION_ONLY_NO_DEPLOYMENT")
        void failed_validationOnly_notEligible() throws Exception {
            String jobId = "rd-ctrl-failed-validate-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.VALIDATION_ONLY_NO_DEPLOYMENT)
                    .failedStage(FailedStage.PRE_VALIDATION)
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.FAILED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(false))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("VALIDATION_ONLY_NO_DEPLOYMENT"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("PRE_VALIDATION"));
        }
    }

    // ── Timed-out job ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timed-out job — rollback recommended")
    class TimedOut {

        @Test
        @DisplayName("TIMED_OUT → rollbackEligible=true, failedStage=TIMEOUT in response")
        void timedOut_rollbackRecommended() throws Exception {
            String jobId = "rd-ctrl-timedout-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(true)
                    .rollbackRecommended(true)
                    .rollbackReasonCode(RollbackReasonCode.TIMED_OUT_DURING_EXECUTION)
                    .failedStage(FailedStage.TIMEOUT)
                    .confidenceNote("Timeout — partial changes may have been applied")
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.TIMED_OUT, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(true))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("TIMED_OUT_DURING_EXECUTION"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("TIMEOUT"))
                    .andExpect(jsonPath("$.rollbackDecision.confidenceNote").isNotEmpty());
        }
    }

    // ── Cancelled job ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cancelled job — no changes applied")
    class Cancelled {

        @Test
        @DisplayName("CANCELLED without apply checkpoint → rollbackEligible=false, CANCELLED_NO_CHANGES_APPLIED")
        void cancelled_noChangesApplied_notEligible() throws Exception {
            String jobId = "rd-ctrl-cancelled-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.CANCELLED_NO_CHANGES_APPLIED)
                    .failedStage(FailedStage.CANCELLATION)
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.CANCELLED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(false))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("CANCELLED_NO_CHANGES_APPLIED"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("CANCELLATION"));
        }
    }

    // ── Unknown checkpoint data ───────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown data — INSUFFICIENT_CHECKPOINT_DATA")
    class UnknownData {

        @Test
        @DisplayName("FAILED with no checkpoints → INSUFFICIENT_CHECKPOINT_DATA, not eligible")
        void failed_noCheckpoints_insufficientData() throws Exception {
            String jobId = "rd-ctrl-unknown-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(false)
                    .rollbackRecommended(false)
                    .rollbackReasonCode(RollbackReasonCode.INSUFFICIENT_CHECKPOINT_DATA)
                    .failedStage(FailedStage.UNKNOWN)
                    .confidenceNote("No checkpoint history available")
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.FAILED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(false))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("INSUFFICIENT_CHECKPOINT_DATA"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("UNKNOWN"));
        }
    }

    // ── Redaction: no sensitive content in diagnosticSummary ─────────────────

    @Nested
    @DisplayName("Safe output — no sensitive content in diagnosticSummary")
    class SafeOutput {

        @Test
        @DisplayName("diagnosticSummary in response does not contain sensitive keywords")
        void diagnosticSummary_noSensitiveContent() throws Exception {
            String jobId = "rd-ctrl-safe-001";
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(true)
                    .rollbackRecommended(true)
                    .rollbackReasonCode(RollbackReasonCode.DEPLOYMENT_PARTIAL_FAILURE)
                    .failedStage(FailedStage.DEPLOYMENT)
                    .diagnosticSummary("Deployment component error — reference metadata definition")
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();

            when(statusAdapter.findByJobId(jobId)).thenReturn(
                    Optional.of(withRollbackDecision(jobId, ReleaseLifecycleState.FAILED, decision)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rollbackDecision.diagnosticSummary")
                            .value("Deployment component error — reference metadata definition"));
        }
    }
}
