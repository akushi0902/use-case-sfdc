package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.resources.v2.release.CheckpointSummary;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LifecycleJobStatusAdapter}.
 *
 * <p>No Spring context required — all collaborators are mocked.
 */
@DisplayName("LifecycleJobStatusAdapter — unit tests")
class LifecycleJobStatusAdapterTest {

    private JobRepository jobRepository;
    private LifecycleJobStatusAdapter adapter;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        adapter = new LifecycleJobStatusAdapter(jobRepository);
    }

    private JobRecord job(String jobId, String state) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId("corr-adapter-test-001");
        r.setOperationType("QUICK_DEPLOY");
        r.setCurrentState(state);
        r.setCreatedAt(Instant.parse("2024-01-01T10:00:00Z"));
        r.setUpdatedAt(Instant.parse("2024-01-01T10:01:00Z"));
        return r;
    }

    private JobCheckpoint checkpoint(String jobId, int seq, String code, String state) {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setJobId(jobId);
        cp.setSequenceNumber(seq);
        cp.setCheckpointCode(code);
        cp.setStateAtCheckpoint(state);
        cp.setSafeMessage("Test checkpoint " + seq);
        cp.setCreatedAt(Instant.parse("2024-01-01T10:00:0" + seq + "Z"));
        return cp;
    }

    // ── findByJobId ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByJobId")
    class FindByJobId {

        @Test
        @DisplayName("unknown jobId returns empty Optional")
        void findByJobId_unknownJob_returnsEmpty() {
            when(jobRepository.findById("job-unknown")).thenReturn(Optional.empty());
            assertThat(adapter.findByJobId("job-unknown")).isEmpty();
        }

        @Test
        @DisplayName("known job returns populated response with all required fields")
        void findByJobId_knownJob_returnsPopulatedResponse() {
            when(jobRepository.findById("job-test-001"))
                    .thenReturn(Optional.of(job("job-test-001", "RUNNING")));
            when(jobRepository.findCheckpointsByJobId("job-test-001"))
                    .thenReturn(List.of(checkpoint("job-test-001", 1, "WORKER_STARTED", "RUNNING")));

            Optional<ReleaseJobStatusResponse> result = adapter.findByJobId("job-test-001");

            assertThat(result).isPresent();
            ReleaseJobStatusResponse resp = result.get();
            assertThat(resp.getJobId()).isEqualTo("job-test-001");
            assertThat(resp.getCorrelationId()).isEqualTo("corr-adapter-test-001");
            assertThat(resp.getOperationType()).isEqualTo("QUICK_DEPLOY");
            assertThat(resp.getState()).isEqualTo(ReleaseLifecycleState.RUNNING);
            assertThat(resp.getAcceptedAt()).isNotNull();
            assertThat(resp.getUpdatedAt()).isNotNull();
            assertThat(resp.getStatusSource()).isEqualTo(LifecycleJobStatusAdapter.STATUS_SOURCE_LABEL);
        }

        @Test
        @DisplayName("job with no checkpoints returns empty checkpoint list")
        void findByJobId_noCheckpoints_returnsEmptyCheckpointList() {
            when(jobRepository.findById("job-accepted-001"))
                    .thenReturn(Optional.of(job("job-accepted-001", "ACCEPTED")));
            when(jobRepository.findCheckpointsByJobId("job-accepted-001")).thenReturn(List.of());

            ReleaseJobStatusResponse resp = adapter.findByJobId("job-accepted-001").orElseThrow();

            assertThat(resp.getCheckpoints()).isEmpty();
            assertThat(resp.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        }
    }

    // ── Lifecycle state mapping ────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle state mapping")
    class LifecycleStateMapping {

        @Test @DisplayName("ACCEPTED maps to ACCEPTED")
        void mapState_accepted() { assertThat(adapter.mapState("ACCEPTED")).isEqualTo(ReleaseLifecycleState.ACCEPTED); }

        @Test @DisplayName("DISPATCHING maps to DISPATCHING")
        void mapState_dispatching() { assertThat(adapter.mapState("DISPATCHING")).isEqualTo(ReleaseLifecycleState.DISPATCHING); }

        @Test @DisplayName("RUNNING maps to RUNNING")
        void mapState_running() { assertThat(adapter.mapState("RUNNING")).isEqualTo(ReleaseLifecycleState.RUNNING); }

        @Test @DisplayName("COMPLETED maps to COMPLETED")
        void mapState_completed() { assertThat(adapter.mapState("COMPLETED")).isEqualTo(ReleaseLifecycleState.COMPLETED); }

        @Test @DisplayName("FAILED maps to FAILED")
        void mapState_failed() { assertThat(adapter.mapState("FAILED")).isEqualTo(ReleaseLifecycleState.FAILED); }

        @Test @DisplayName("CANCELLED maps to CANCELLED")
        void mapState_cancelled() { assertThat(adapter.mapState("CANCELLED")).isEqualTo(ReleaseLifecycleState.CANCELLED); }

        @Test @DisplayName("TIMED_OUT maps to TIMED_OUT")
        void mapState_timedOut() { assertThat(adapter.mapState("TIMED_OUT")).isEqualTo(ReleaseLifecycleState.TIMED_OUT); }

        @Test @DisplayName("REJECTED maps to FAILED (internal-only state not in public vocabulary)")
        void mapState_rejected_mapsToFailed() { assertThat(adapter.mapState("REJECTED")).isEqualTo(ReleaseLifecycleState.FAILED); }

        @Test @DisplayName("null state string falls back to ACCEPTED")
        void mapState_null_fallsBackToAccepted() { assertThat(adapter.mapState(null)).isEqualTo(ReleaseLifecycleState.ACCEPTED); }

        @Test @DisplayName("unknown state string falls back to ACCEPTED")
        void mapState_unknown_fallsBackToAccepted() { assertThat(adapter.mapState("BOGUS_STATE")).isEqualTo(ReleaseLifecycleState.ACCEPTED); }
    }

    // ── Checkpoint ordering ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Checkpoint ordering")
    class CheckpointOrdering {

        @Test
        @DisplayName("checkpoints are returned sorted by sequenceNumber ascending regardless of list order")
        void findByJobId_checkpointsOutOfOrder_returnsSortedBySequence() {
            when(jobRepository.findById("job-order-001"))
                    .thenReturn(Optional.of(job("job-order-001", "RUNNING")));
            // Intentionally out of order
            when(jobRepository.findCheckpointsByJobId("job-order-001"))
                    .thenReturn(List.of(
                            checkpoint("job-order-001", 3, "STEP_3", "RUNNING"),
                            checkpoint("job-order-001", 1, "STEP_1", "RUNNING"),
                            checkpoint("job-order-001", 2, "STEP_2", "RUNNING")));

            ReleaseJobStatusResponse resp = adapter.findByJobId("job-order-001").orElseThrow();

            List<CheckpointSummary> checkpoints = resp.getCheckpoints();
            assertThat(checkpoints).hasSize(3);
            assertThat(checkpoints.get(0).getSequenceNumber()).isEqualTo(1);
            assertThat(checkpoints.get(1).getSequenceNumber()).isEqualTo(2);
            assertThat(checkpoints.get(2).getSequenceNumber()).isEqualTo(3);
        }
    }

    // ── Safe diagnostic summarization ─────────────────────────────────────────

    @Nested
    @DisplayName("Safe diagnostic summarization")
    class SafeDiagnosticSummarization {

        @Test
        @DisplayName("FAILED job with checkpoints returns safe diagnostic from last checkpoint message")
        void findByJobId_failedJobWithCheckpoints_returnsSafeDiagnostic() {
            when(jobRepository.findById("job-failed-001"))
                    .thenReturn(Optional.of(job("job-failed-001", "FAILED")));
            JobCheckpoint failedCp = checkpoint("job-failed-001", 2, "EXECUTION_FAILED", "FAILED");
            failedCp.setSafeMessage("Deployment failed: package version mismatch");
            when(jobRepository.findCheckpointsByJobId("job-failed-001"))
                    .thenReturn(List.of(
                            checkpoint("job-failed-001", 1, "WORKER_STARTED", "RUNNING"),
                            failedCp));

            ReleaseJobStatusResponse resp = adapter.findByJobId("job-failed-001").orElseThrow();

            assertThat(resp.getSafeDiagnosticSummary())
                    .isEqualTo("Deployment failed: package version mismatch");
        }

        @Test
        @DisplayName("RUNNING job has no diagnostic summary")
        void findByJobId_runningJob_nodiagnosticSummary() {
            when(jobRepository.findById("job-running-001"))
                    .thenReturn(Optional.of(job("job-running-001", "RUNNING")));
            when(jobRepository.findCheckpointsByJobId("job-running-001")).thenReturn(List.of());

            ReleaseJobStatusResponse resp = adapter.findByJobId("job-running-001").orElseThrow();
            assertThat(resp.getSafeDiagnosticSummary()).isNull();
        }

        @Test
        @DisplayName("safeMessage longer than MAX_DIAGNOSTIC_LENGTH is truncated in checkpoint summary")
        void findByJobId_longSafeMessage_truncatedInCheckpointSummary() {
            String longMsg = "a".repeat(600);
            JobCheckpoint cp = checkpoint("job-trunc-001", 1, "STEP", "RUNNING");
            cp.setSafeMessage(longMsg);

            when(jobRepository.findById("job-trunc-001"))
                    .thenReturn(Optional.of(job("job-trunc-001", "RUNNING")));
            when(jobRepository.findCheckpointsByJobId("job-trunc-001")).thenReturn(List.of(cp));

            ReleaseJobStatusResponse resp = adapter.findByJobId("job-trunc-001").orElseThrow();
            assertThat(resp.getCheckpoints().get(0).getSafeMessage())
                    .hasSize(LifecycleJobStatusAdapter.MAX_DIAGNOSTIC_LENGTH + 3); // +3 for "..."
        }
    }

    // ── Adapter fallback behavior ──────────────────────────────────────────────

    @Nested
    @DisplayName("Adapter fallback behavior")
    class AdapterFallbackBehavior {

        @Test
        @DisplayName("checkpoint fetch failure returns job without checkpoints — does not fail the whole response")
        void findByJobId_checkpointFetchFails_returnsJobWithoutCheckpoints() {
            when(jobRepository.findById("job-cp-fail-001"))
                    .thenReturn(Optional.of(job("job-cp-fail-001", "RUNNING")));
            when(jobRepository.findCheckpointsByJobId("job-cp-fail-001"))
                    .thenThrow(new LifecyclePersistenceException("DB connection timeout"));

            Optional<ReleaseJobStatusResponse> result = adapter.findByJobId("job-cp-fail-001");

            assertThat(result).isPresent();
            assertThat(result.get().getCheckpoints()).isEmpty();
        }
    }
}
