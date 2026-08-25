package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.lifecycle.LifecycleTransitionException;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckpointPersistenceAdapter}.
 *
 * <p>No Spring context required — all collaborators are mocked.
 */
@DisplayName("CheckpointPersistenceAdapter — unit tests")
class CheckpointPersistenceAdapterTest {

    private CheckpointMapper mapper;
    private JobRepository jobRepository;
    private JobLifecycleService lifecycleService;
    private CheckpointPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new CheckpointMapper(); // use real mapper
        jobRepository = mock(JobRepository.class);
        lifecycleService = mock(JobLifecycleService.class);
        adapter = new CheckpointPersistenceAdapter(mapper, jobRepository, lifecycleService);
    }

    private CheckpointEvent validRunningEvent() {
        CheckpointEvent e = new CheckpointEvent();
        e.setJobId("job-adapter-fixture-001");
        e.setCorrelationId("corr-adapter-fixture-001");
        e.setCheckpointCode("WORKER_STARTED");
        e.setStatusCategory("RUNNING");
        e.setSequenceNumber(1);
        e.setEventTimestamp("2024-01-01T10:00:00.000Z");
        e.setSafeMessage("Worker pod started execution");
        e.setMessageId("msg-adapter-fixture-001");
        return e;
    }

    private CheckpointEvent validCompletedEvent() {
        CheckpointEvent e = new CheckpointEvent();
        e.setJobId("job-adapter-fixture-001");
        e.setCorrelationId("corr-adapter-fixture-001");
        e.setCheckpointCode("EXECUTION_COMPLETED");
        e.setStatusCategory("COMPLETED");
        e.setSequenceNumber(3);
        e.setEventTimestamp("2024-01-01T10:05:00.000Z");
        return e;
    }

    private CheckpointEvent validProgressEvent() {
        CheckpointEvent e = new CheckpointEvent();
        e.setJobId("job-adapter-fixture-001");
        e.setCorrelationId("corr-adapter-fixture-001");
        e.setCheckpointCode("DEPLOY_STEP_COMPLETE");
        e.setStatusCategory("PROGRESS");
        e.setSequenceNumber(2);
        return e;
    }

    private JobRecord existingJob(String jobId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId("corr-adapter-fixture-001");
        r.setCurrentState(JobLifecycleState.ACCEPTED.name());
        r.setCreatedAt(Instant.now());
        r.setVersion(0L);
        return r;
    }

    // ── Successful persistence ────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful persistence")
    class SuccessfulPersistence {

        @Test
        @DisplayName("RUNNING event persists checkpoint and marks job RUNNING")
        void process_runningEvent_persistsCheckpointAndTransitions() {
            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-001")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(Collections.emptyList());

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validRunningEvent());

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);
            verify(jobRepository).saveCheckpoint(any(JobCheckpoint.class));
            verify(lifecycleService).markRunning(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("COMPLETED event persists checkpoint and marks job COMPLETED")
        void process_completedEvent_persistsAndMarksCompleted() {
            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-001")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(Collections.emptyList());

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validCompletedEvent());

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);
            verify(lifecycleService).markCompleted(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("PROGRESS event persists checkpoint record without any lifecycle state transition")
        void process_progressEvent_persistsCheckpointOnly() {
            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-001")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(Collections.emptyList());

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validProgressEvent());

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);
            verify(jobRepository).saveCheckpoint(any(JobCheckpoint.class));
            // No state transition for PROGRESS
            verify(lifecycleService, never()).markRunning(anyString(), anyString(), anyInt());
            verify(lifecycleService, never()).markCompleted(anyString(), anyString(), anyInt());
        }
    }

    // ── Malformed events ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Malformed event handling")
    class MalformedEventHandling {

        @Test
        @DisplayName("malformed event (missing jobId) returns MALFORMED and does not touch repository")
        void process_malformedEvent_returnsMalformedNoRepoInteraction() {
            CheckpointEvent malformed = new CheckpointEvent();
            malformed.setCheckpointCode("WORKER_STARTED");
            // no jobId

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(malformed);

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.MALFORMED);
            verify(jobRepository, never()).saveCheckpoint(any());
            verify(lifecycleService, never()).markRunning(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("null event returns MALFORMED")
        void process_nullEvent_returnsMalformed() {
            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(null);
            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.MALFORMED);
        }
    }

    // ── Orphaned events ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Orphaned event handling (job not found)")
    class OrphanedEventHandling {

        @Test
        @DisplayName("event for unknown jobId returns ORPHANED without persisting")
        void process_unknownJob_returnsOrphanedNoCheckpointSaved() {
            when(jobRepository.findById("job-adapter-fixture-unknown"))
                    .thenReturn(Optional.empty());
            when(jobRepository.findCheckpointsByJobId(anyString()))
                    .thenReturn(Collections.emptyList());

            CheckpointEvent e = validRunningEvent();
            e.setJobId("job-adapter-fixture-unknown");

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(e);

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.ORPHANED);
            verify(jobRepository, never()).saveCheckpoint(any());
        }
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate event detection")
    class DuplicateEventDetection {

        @Test
        @DisplayName("duplicate checkpoint (same jobId + sequenceNumber) returns DUPLICATE without saving again")
        void process_duplicateCheckpoint_returnsDuplicateNoSave() {
            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-001")));

            JobCheckpoint existing = new JobCheckpoint();
            existing.setJobId("job-adapter-fixture-001");
            existing.setSequenceNumber(1);
            existing.setCheckpointCode("WORKER_STARTED");
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(List.of(existing));

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validRunningEvent());

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.DUPLICATE);
            verify(jobRepository, never()).saveCheckpoint(any());
            verify(lifecycleService, never()).markRunning(anyString(), anyString(), anyInt());
        }
    }

    // ── Lifecycle transition policy violations ────────────────────────────────

    @Nested
    @DisplayName("Terminal state replay and transition policy violations")
    class TerminalStateReplay {

        @Test
        @DisplayName("COMPLETED event for job already in FAILED terminal state: checkpoint persisted but state not overwritten")
        void process_completedEventOnTerminalJob_checkpointPersistedTransitionSkipped() {
            JobRecord terminalJob = existingJob("job-adapter-fixture-001");
            terminalJob.setCurrentState(JobLifecycleState.FAILED.name());

            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(terminalJob));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(Collections.emptyList());
            doThrow(new LifecycleTransitionException("FAILED → COMPLETED is not in the allowed matrix"))
                    .when(lifecycleService).markCompleted(anyString(), anyString(), anyInt());

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validCompletedEvent());

            // Checkpoint IS persisted even if transition fails
            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);
            verify(jobRepository).saveCheckpoint(any(JobCheckpoint.class));
        }
    }

    // ── State update calls via lifecycle service ──────────────────────────────

    @Nested
    @DisplayName("State update calls to lifecycle service")
    class StateUpdateCalls {

        @Test
        @DisplayName("FAILED event calls markFailed with safe reason code")
        void process_failedEvent_callsMarkFailed() {
            when(jobRepository.findById("job-adapter-fixture-002"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-002")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-002"))
                    .thenReturn(Collections.emptyList());

            CheckpointEvent failedEvent = new CheckpointEvent();
            failedEvent.setJobId("job-adapter-fixture-002");
            failedEvent.setCorrelationId("corr-adapter-fixture-002");
            failedEvent.setCheckpointCode("EXECUTION_FAILED");
            failedEvent.setStatusCategory("FAILED");
            failedEvent.setSequenceNumber(2);
            failedEvent.setSafeReasonCode("PACKAGE_VERSION_MISMATCH");

            adapter.process(failedEvent);

            verify(lifecycleService).markFailed(
                    anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("CANCELLED event calls markCancelled with reason code")
        void process_cancelledEvent_callsMarkCancelled() {
            when(jobRepository.findById("job-adapter-fixture-003"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-003")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-003"))
                    .thenReturn(Collections.emptyList());

            CheckpointEvent cancelledEvent = new CheckpointEvent();
            cancelledEvent.setJobId("job-adapter-fixture-003");
            cancelledEvent.setCheckpointCode("JOB_CANCELLED");
            cancelledEvent.setStatusCategory("CANCELLED");
            cancelledEvent.setSequenceNumber(2);
            cancelledEvent.setSafeReasonCode("OPERATOR_CANCELLED");

            adapter.process(cancelledEvent);

            verify(lifecycleService).markCancelled(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("repository saveCheckpoint failure is handled without crashing — PERSISTENCE_ERROR returned")
        void process_saveCheckpointFails_returnsPersistenceError() {
            when(jobRepository.findById("job-adapter-fixture-001"))
                    .thenReturn(Optional.of(existingJob("job-adapter-fixture-001")));
            when(jobRepository.findCheckpointsByJobId("job-adapter-fixture-001"))
                    .thenReturn(Collections.emptyList());
            doThrow(new LifecyclePersistenceException("disk full"))
                    .when(jobRepository).saveCheckpoint(any(JobCheckpoint.class));

            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(validRunningEvent());

            assertThat(outcome).isEqualTo(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTENCE_ERROR);
            // State transition should not be called if checkpoint save failed
            verify(lifecycleService, times(0)).markRunning(anyString(), anyString(), anyInt());
        }
    }
}
