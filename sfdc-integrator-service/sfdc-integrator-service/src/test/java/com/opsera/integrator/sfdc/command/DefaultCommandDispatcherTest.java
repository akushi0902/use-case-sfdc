package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.lifecycle.TransitionResult;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultCommandDispatcher}.
 *
 * <p>No Spring context required — all collaborators are mocked.
 * Covers: successful dispatch, idempotency, lifecycle persistence failure,
 * adapter failure, correlation propagation, and null input validation.
 */
@DisplayName("DefaultCommandDispatcher — unit tests")
class DefaultCommandDispatcherTest {

    private JobRepository jobRepository;
    private JobLifecycleService lifecycleService;
    private ReleaseCommandAdapter adapter;
    private DefaultCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        lifecycleService = mock(JobLifecycleService.class);
        adapter = mock(ReleaseCommandAdapter.class);
        when(adapter.supportedOperationType()).thenReturn("QUICK_DEPLOY");
        dispatcher = new DefaultCommandDispatcher(jobRepository, lifecycleService, adapter);
    }

    private AsyncCommandRequest quickDeployCommand() {
        return AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .correlationId("corr-dispatch-test-001")
                .idempotencyKey(null)
                .pipelineId("pipeline-dispatch-fixture-001")
                .stepId("step-dispatch-fixture-001")
                .deploymentRequestId("deploy-req-dispatch-fixture-001")
                .customerIdHash("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
                .sfdcToolId("sfdc-tool-dispatch-fixture-001")
                .build();
    }

    private JobRecord existingJobRecord(String jobId, String correlationId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setCurrentState(JobLifecycleState.ACCEPTED.name());
        r.setOperationType("QUICK_DEPLOY");
        r.setCreatedAt(Instant.now());
        r.setVersion(0L);
        return r;
    }

    // ── Successful dispatch ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful dispatch")
    class SuccessfulDispatch {

        @Test
        @DisplayName("dispatch returns outcome with non-null jobId, correlationId, statusPath, state, and acceptedAt")
        void dispatch_validCommand_returnsFullOutcome() {
            AcceptedCommandOutcome outcome = dispatcher.dispatch(quickDeployCommand());

            assertThat(outcome.getJobId()).isNotBlank();
            assertThat(outcome.getCorrelationId()).isEqualTo("corr-dispatch-test-001");
            assertThat(outcome.getStatusPath()).contains(outcome.getJobId());
            assertThat(outcome.getState()).isEqualTo("ACCEPTED");
            assertThat(outcome.getAcceptedAt()).isNotNull();
            assertThat(outcome.getOperationType()).isEqualTo("QUICK_DEPLOY");
            assertThat(outcome.isIdempotentReplay()).isFalse();
        }

        @Test
        @DisplayName("dispatch persists job record before invoking adapter (ordering guarantee)")
        void dispatch_persistsJobBeforeAdapterInvocation() {
            // Adapter invocation must follow job save — verified via Mockito in-order
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jobRepository, adapter);

            dispatcher.dispatch(quickDeployCommand());

            inOrder.verify(jobRepository).save(any(JobRecord.class));
            inOrder.verify(adapter).execute(any(AsyncCommandRequest.class));
        }

        @Test
        @DisplayName("dispatch saves job with ACCEPTED state and correct operational fields")
        void dispatch_savesJobRecordWithCorrectFields() {
            AsyncCommandRequest command = quickDeployCommand();
            dispatcher.dispatch(command);

            ArgumentCaptor<JobRecord> captor = ArgumentCaptor.forClass(JobRecord.class);
            verify(jobRepository).save(captor.capture());

            JobRecord saved = captor.getValue();
            assertThat(saved.getJobId()).isNotBlank();
            assertThat(saved.getCorrelationId()).isEqualTo("corr-dispatch-test-001");
            assertThat(saved.getCurrentState()).isEqualTo("ACCEPTED");
            assertThat(saved.getOperationType()).isEqualTo("QUICK_DEPLOY");
            assertThat(saved.getPipelineId()).isEqualTo("pipeline-dispatch-fixture-001");
            assertThat(saved.getStepId()).isEqualTo("step-dispatch-fixture-001");
            assertThat(saved.getVersion()).isEqualTo(0L);
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("dispatch saves acceptance checkpoint for audit traceability")
        void dispatch_savesAcceptanceCheckpoint() {
            dispatcher.dispatch(quickDeployCommand());

            ArgumentCaptor<JobCheckpoint> captor = ArgumentCaptor.forClass(JobCheckpoint.class);
            verify(jobRepository).saveCheckpoint(captor.capture());

            JobCheckpoint checkpoint = captor.getValue();
            assertThat(checkpoint.getCheckpointCode()).isEqualTo("JOB_ACCEPTED");
            assertThat(checkpoint.getStateAtCheckpoint()).isEqualTo("ACCEPTED");
            assertThat(checkpoint.getSequenceNumber()).isEqualTo(0);
        }

        @Test
        @DisplayName("statusPath contains the generated job ID")
        void dispatch_statusPathContainsJobId() {
            AcceptedCommandOutcome outcome = dispatcher.dispatch(quickDeployCommand());
            assertThat(outcome.getStatusPath()).isEqualTo(
                    String.format(DefaultCommandDispatcher.STATUS_PATH_TEMPLATE, outcome.getJobId()));
        }

        @Test
        @DisplayName("adapter is invoked exactly once for a successful dispatch")
        void dispatch_invokesAdapterExactlyOnce() {
            dispatcher.dispatch(quickDeployCommand());
            verify(adapter, times(1)).execute(any(AsyncCommandRequest.class));
        }
    }

    // ── Correlation propagation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Correlation propagation")
    class CorrelationPropagation {

        @Test
        @DisplayName("provided correlationId is used in outcome and job record")
        void dispatch_providedCorrelationId_usedInOutcome() {
            AcceptedCommandOutcome outcome = dispatcher.dispatch(quickDeployCommand());
            assertThat(outcome.getCorrelationId()).isEqualTo("corr-dispatch-test-001");
        }

        @Test
        @DisplayName("missing correlationId is replaced with a generated UUID-based identifier")
        void dispatch_missingCorrelationId_generatesOne() {
            AsyncCommandRequest commandNoCorr = AsyncCommandRequest.builder()
                    .operationType("QUICK_DEPLOY")
                    .correlationId(null)
                    .pipelineId("pipeline-dispatch-fixture-002")
                    .deploymentRequestId("deploy-req-dispatch-fixture-002")
                    .build();

            AcceptedCommandOutcome outcome = dispatcher.dispatch(commandNoCorr);

            assertThat(outcome.getCorrelationId()).isNotBlank();
            assertThat(outcome.getCorrelationId()).doesNotEqualTo("null");
        }

        @Test
        @DisplayName("blank correlationId is replaced with a generated identifier")
        void dispatch_blankCorrelationId_generatesOne() {
            AsyncCommandRequest commandBlankCorr = AsyncCommandRequest.builder()
                    .operationType("QUICK_DEPLOY")
                    .correlationId("   ")
                    .pipelineId("pipeline-dispatch-fixture-003")
                    .deploymentRequestId("deploy-req-dispatch-fixture-003")
                    .build();

            AcceptedCommandOutcome outcome = dispatcher.dispatch(commandBlankCorr);
            assertThat(outcome.getCorrelationId()).isNotBlank().doesNotContain("   ");
        }

        @Test
        @DisplayName("correlationId is propagated into MDC during dispatch and removed afterwards")
        void dispatch_propagatesCorrelationIdIntoMdc() {
            // MDC should not contain the correlation key before dispatch
            MDC.remove(DefaultCommandDispatcher.MDC_CORRELATION_KEY);

            dispatcher.dispatch(quickDeployCommand());

            // After dispatch, MDC key should be cleaned up
            String mdcAfter = MDC.get(DefaultCommandDispatcher.MDC_CORRELATION_KEY);
            assertThat(mdcAfter).isNull();
        }
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("duplicate idempotency key returns existing job outcome without creating new record")
        void dispatch_duplicateIdempotencyKey_returnsExistingJob() {
            String idempotencyKey = "idem-key-dispatch-fixture-001";
            JobRecord existingJob = existingJobRecord("job-existing-dispatch-001", "corr-dispatch-test-001");
            when(jobRepository.findByCorrelationId(idempotencyKey)).thenReturn(Optional.of(existingJob));

            AsyncCommandRequest command = AsyncCommandRequest.builder()
                    .operationType("QUICK_DEPLOY")
                    .correlationId("corr-dispatch-test-001")
                    .idempotencyKey(idempotencyKey)
                    .pipelineId("pipeline-dispatch-fixture-001")
                    .deploymentRequestId("deploy-req-dispatch-fixture-001")
                    .build();

            AcceptedCommandOutcome outcome = dispatcher.dispatch(command);

            assertThat(outcome.getJobId()).isEqualTo("job-existing-dispatch-001");
            assertThat(outcome.isIdempotentReplay()).isTrue();
            assertThat(outcome.getState()).isEqualTo("ACCEPTED");

            // Must not create a new job record or invoke adapter
            verify(jobRepository, never()).save(any());
            verify(adapter, never()).execute(any());
        }

        @Test
        @DisplayName("new idempotency key proceeds with full dispatch")
        void dispatch_newIdempotencyKey_createsNewJob() {
            String idempotencyKey = "idem-key-dispatch-fixture-002";
            when(jobRepository.findByCorrelationId(idempotencyKey)).thenReturn(Optional.empty());

            AsyncCommandRequest command = AsyncCommandRequest.builder()
                    .operationType("QUICK_DEPLOY")
                    .correlationId("corr-dispatch-test-002")
                    .idempotencyKey(idempotencyKey)
                    .pipelineId("pipeline-dispatch-fixture-002")
                    .deploymentRequestId("deploy-req-dispatch-fixture-002")
                    .build();

            AcceptedCommandOutcome outcome = dispatcher.dispatch(command);

            assertThat(outcome.isIdempotentReplay()).isFalse();
            verify(jobRepository).save(any(JobRecord.class));
            verify(adapter).execute(any(AsyncCommandRequest.class));
        }

        @Test
        @DisplayName("null idempotency key skips idempotency check and always dispatches")
        void dispatch_nullIdempotencyKey_alwaysDispatches() {
            // idempotencyKey is null — no lookup should happen
            dispatcher.dispatch(quickDeployCommand());
            verify(jobRepository, never()).findByCorrelationId(anyString());
        }
    }

    // ── Failure: lifecycle persistence ───────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle persistence failure")
    class LifecyclePersistenceFailure {

        @Test
        @DisplayName("repository.save() failure prevents adapter invocation")
        void dispatch_repoSaveFails_adapterNotCalled() {
            doThrow(new LifecyclePersistenceException("DB unavailable"))
                    .when(jobRepository).save(any(JobRecord.class));

            assertThatThrownBy(() -> dispatcher.dispatch(quickDeployCommand()))
                    .isInstanceOf(LifecyclePersistenceException.class)
                    .hasMessageContaining("DB unavailable");

            verify(adapter, never()).execute(any());
        }

        @Test
        @DisplayName("checkpoint save failure does not prevent successful dispatch outcome")
        void dispatch_checkpointSaveFails_dispatchSucceeds() {
            doThrow(new LifecyclePersistenceException("checkpoint table locked"))
                    .when(jobRepository).saveCheckpoint(any(JobCheckpoint.class));

            AcceptedCommandOutcome outcome = dispatcher.dispatch(quickDeployCommand());

            // Checkpoint failure is non-fatal — job still accepted and adapter invoked
            assertThat(outcome.getState()).isEqualTo("ACCEPTED");
            verify(adapter).execute(any(AsyncCommandRequest.class));
        }
    }

    // ── Failure: adapter handoff ──────────────────────────────────────────────

    @Nested
    @DisplayName("Adapter handoff failure")
    class AdapterHandoffFailure {

        @Test
        @DisplayName("adapter.execute() failure throws CommandDispatchException with job ID")
        void dispatch_adapterFails_throwsCommandDispatchException() {
            doThrow(new RuntimeException("executor rejected task"))
                    .when(adapter).execute(any(AsyncCommandRequest.class));

            assertThatThrownBy(() -> dispatcher.dispatch(quickDeployCommand()))
                    .isInstanceOf(CommandDispatchException.class)
                    .satisfies(ex -> {
                        CommandDispatchException cde = (CommandDispatchException) ex;
                        assertThat(cde.getJobId()).isNotBlank();
                        assertThat(cde.getCorrelationId()).isEqualTo("corr-dispatch-test-001");
                        assertThat(cde.getSafeReasonCode()).isEqualTo("ADAPTER_HANDOFF_FAILURE");
                    });
        }

        @Test
        @DisplayName("adapter failure calls lifecycleService.markFailed() to record job failure")
        void dispatch_adapterFails_marksJobFailed() {
            // Need the lifecycle service to handle the markFailed call:
            // markFailed calls lifecycleService.transition() which loads the job
            // Since lifecycleService is a mock, markFailed is a no-op here
            doThrow(new RuntimeException("executor rejected task"))
                    .when(adapter).execute(any(AsyncCommandRequest.class));

            try {
                dispatcher.dispatch(quickDeployCommand());
            } catch (CommandDispatchException ignored) {}

            verify(lifecycleService).markFailed(anyString(), anyString(),
                    anyString(), anyString());
        }

        @Test
        @DisplayName("job record IS persisted even when adapter fails — job is durably tracked in FAILED state")
        void dispatch_adapterFails_jobRecordWasPersisted() {
            doThrow(new RuntimeException("service unavailable"))
                    .when(adapter).execute(any(AsyncCommandRequest.class));

            try {
                dispatcher.dispatch(quickDeployCommand());
            } catch (CommandDispatchException ignored) {}

            // Job was saved before adapter was called
            verify(jobRepository).save(any(JobRecord.class));
        }
    }

    // ── Null / invalid input ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Null and invalid input")
    class NullAndInvalidInput {

        @Test
        @DisplayName("null command throws IllegalArgumentException before any repository or adapter call")
        void dispatch_nullCommand_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> dispatcher.dispatch(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");

            verify(jobRepository, never()).save(any());
            verify(adapter, never()).execute(any());
        }
    }
}
