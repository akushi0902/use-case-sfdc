package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CheckpointMapper}.
 *
 * <p>No Spring context required — mapper is a pure component.
 */
@DisplayName("CheckpointMapper — unit tests")
class CheckpointMapperTest {

    private CheckpointMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CheckpointMapper();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CheckpointEvent event(String jobId, String code, String category, int seq) {
        CheckpointEvent e = new CheckpointEvent();
        e.setJobId(jobId);
        e.setCheckpointCode(code);
        e.setStatusCategory(category);
        e.setSequenceNumber(seq);
        e.setCorrelationId("corr-mapper-test-001");
        e.setEventTimestamp("2024-01-01T10:00:00.000Z");
        e.setSafeMessage("Unit test checkpoint message");
        e.setMessageId("msg-mapper-test-001");
        return e;
    }

    // ── Valid events ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid event mapping")
    class ValidEvents {

        @Test
        @DisplayName("RUNNING category event produces PERSIST outcome with RUNNING state transition")
        void map_runningCategory_producesRunningTransition() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-001", "WORKER_STARTED", "RUNNING", 1));

            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.PERSIST);
            assertThat(result.hasStateTransition()).isTrue();
            assertThat(result.getStateTransition().getTargetState()).isEqualTo(JobLifecycleState.RUNNING);
        }

        @Test
        @DisplayName("COMPLETED category event produces PERSIST outcome with COMPLETED state transition")
        void map_completedCategory_producesCompletedTransition() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-001", "EXECUTION_COMPLETED", "COMPLETED", 3));

            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.PERSIST);
            assertThat(result.getStateTransition().getTargetState()).isEqualTo(JobLifecycleState.COMPLETED);
        }

        @Test
        @DisplayName("FAILED category event produces PERSIST outcome with FAILED transition and reason code")
        void map_failedCategory_producesFailedTransitionWithReasonCode() {
            CheckpointEvent e = event("job-mapper-fixture-002", "EXECUTION_FAILED", "FAILED", 2);
            e.setSafeReasonCode("PACKAGE_VERSION_MISMATCH");

            CheckpointMappingResult result = mapper.map(e);

            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.PERSIST);
            assertThat(result.getStateTransition().getTargetState()).isEqualTo(JobLifecycleState.FAILED);
            assertThat(result.getStateTransition().getSafeReasonCode()).isEqualTo("PACKAGE_VERSION_MISMATCH");
        }

        @Test
        @DisplayName("CANCELLED category event produces CANCELLED transition with reason code")
        void map_cancelledCategory_producesCancelledTransition() {
            CheckpointEvent e = event("job-mapper-fixture-003", "JOB_CANCELLED", "CANCELLED", 2);
            e.setSafeReasonCode("OPERATOR_CANCELLED");

            CheckpointMappingResult result = mapper.map(e);

            assertThat(result.getStateTransition().getTargetState()).isEqualTo(JobLifecycleState.CANCELLED);
            assertThat(result.getStateTransition().getSafeReasonCode()).isEqualTo("OPERATOR_CANCELLED");
        }

        @Test
        @DisplayName("TIMED_OUT category event produces TIMED_OUT transition")
        void map_timedOutCategory_producesTimedOutTransition() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-004", "EXECUTION_TIMEOUT", "TIMED_OUT", 5));

            assertThat(result.getStateTransition().getTargetState()).isEqualTo(JobLifecycleState.TIMED_OUT);
        }

        @Test
        @DisplayName("PROGRESS category event produces PERSIST outcome with NO state transition")
        void map_progressCategory_noStateTransition() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-001", "DEPLOY_STEP_COMPLETE", "PROGRESS", 2));

            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.PERSIST);
            assertThat(result.hasStateTransition()).isFalse();
        }

        @Test
        @DisplayName("unknown category event produces PERSIST outcome with NO state transition")
        void map_unknownCategory_noStateTransition() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-004", "CUSTOM_STEP", "CUSTOM_CATEGORY", 1));

            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.PERSIST);
            assertThat(result.hasStateTransition()).isFalse();
        }

        @Test
        @DisplayName("checkpoint record has correct jobId, code, seq, and stateAtCheckpoint")
        void map_validEvent_checkpointHasCorrectFields() {
            CheckpointMappingResult result = mapper.map(
                    event("job-mapper-fixture-001", "WORKER_STARTED", "RUNNING", 1));

            JobCheckpoint cp = result.getCheckpoint();
            assertThat(cp.getJobId()).isEqualTo("job-mapper-fixture-001");
            assertThat(cp.getCheckpointCode()).isEqualTo("WORKER_STARTED");
            assertThat(cp.getSequenceNumber()).isEqualTo(1);
            assertThat(cp.getStateAtCheckpoint()).isEqualTo("RUNNING");
            assertThat(cp.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("event key is deterministic: same jobId+code+seq always produces the same key")
        void map_sameEvent_deterministicEventKey() {
            CheckpointEvent e1 = event("job-mapper-fixture-001", "WORKER_STARTED", "RUNNING", 1);
            CheckpointEvent e2 = event("job-mapper-fixture-001", "WORKER_STARTED", "RUNNING", 1);

            String key1 = mapper.buildEventKey(e1);
            String key2 = mapper.buildEventKey(e2);

            assertThat(key1).isEqualTo(key2);
            assertThat(key1).contains("job-mapper-fixture-001").contains("WORKER_STARTED").contains("1");
        }

        @Test
        @DisplayName("different sequence numbers produce different event keys")
        void map_differentSequence_differentEventKey() {
            CheckpointEvent e1 = event("job-mapper-fixture-001", "STEP", "PROGRESS", 1);
            CheckpointEvent e2 = event("job-mapper-fixture-001", "STEP", "PROGRESS", 2);

            assertThat(mapper.buildEventKey(e1)).isNotEqualTo(mapper.buildEventKey(e2));
        }
    }

    // ── Malformed events ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Malformed event handling")
    class MalformedEvents {

        @Test
        @DisplayName("null event returns MALFORMED outcome")
        void map_nullEvent_returnsMalformed() {
            CheckpointMappingResult result = mapper.map(null);
            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.MALFORMED);
        }

        @Test
        @DisplayName("event with blank jobId returns MALFORMED outcome")
        void map_blankJobId_returnsMalformed() {
            CheckpointEvent e = event("   ", "WORKER_STARTED", "RUNNING", 1);
            CheckpointMappingResult result = mapper.map(e);
            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.MALFORMED);
            assertThat(result.getSafeSkipReason()).contains("jobId");
        }

        @Test
        @DisplayName("event with null jobId returns MALFORMED outcome")
        void map_nullJobId_returnsMalformed() {
            CheckpointEvent e = new CheckpointEvent();
            e.setCheckpointCode("WORKER_STARTED");
            CheckpointMappingResult result = mapper.map(e);
            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.MALFORMED);
        }

        @Test
        @DisplayName("event with blank checkpointCode returns MALFORMED outcome")
        void map_blankCheckpointCode_returnsMalformed() {
            CheckpointEvent e = event("job-mapper-fixture-001", "   ", "RUNNING", 1);
            CheckpointMappingResult result = mapper.map(e);
            assertThat(result.getOutcome()).isEqualTo(CheckpointMappingResult.MappingOutcome.MALFORMED);
            assertThat(result.getSafeSkipReason()).contains("checkpointCode");
        }
    }

    // ── Safe message sanitization ─────────────────────────────────────────────

    @Nested
    @DisplayName("Safe message sanitization")
    class SafeMessageSanitization {

        @Test
        @DisplayName("safeMessage longer than 512 chars is truncated")
        void sanitizeSafeMessage_longMessage_truncated() {
            String longMsg = "a".repeat(600);
            String sanitized = mapper.sanitizeSafeMessage(longMsg);
            assertThat(sanitized).hasSize(CheckpointMapper.MAX_SAFE_MESSAGE_LENGTH);
            assertThat(sanitized).endsWith("...");
        }

        @Test
        @DisplayName("40+ char hex string in safeMessage is redacted")
        void sanitizeSafeMessage_hexToken_redacted() {
            String withToken = "Deploy completed. Token: abcdef1234567890abcdef1234567890abcdef12";
            String sanitized = mapper.sanitizeSafeMessage(withToken);
            assertThat(sanitized).doesNotContain("abcdef1234567890abcdef1234567890abcdef12");
            assertThat(sanitized).contains("[REDACTED]");
        }

        @Test
        @DisplayName("Bearer token in safeMessage is redacted")
        void sanitizeSafeMessage_bearerToken_redacted() {
            String withBearer = "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.sometoken";
            String sanitized = mapper.sanitizeSafeMessage(withBearer);
            assertThat(sanitized).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
        }

        @Test
        @DisplayName("null safeMessage returns null without error")
        void sanitizeSafeMessage_null_returnsNull() {
            assertThat(mapper.sanitizeSafeMessage(null)).isNull();
        }

        @Test
        @DisplayName("normal safe message passes through unmodified")
        void sanitizeSafeMessage_normalMessage_unchanged() {
            String safe = "Deploy step completed successfully";
            assertThat(mapper.sanitizeSafeMessage(safe)).isEqualTo(safe);
        }
    }
}
