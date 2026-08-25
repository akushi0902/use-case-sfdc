package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-boundary integration tests for the command dispatch path.
 *
 * <p>Uses real {@link DefaultCommandDispatcher} with a mocked {@link JobRepository}
 * and a test-double {@link ReleaseCommandAdapter} to verify that:
 * <ul>
 *   <li>A job is durably persisted in {@code ACCEPTED} state before the adapter is invoked.</li>
 *   <li>The accepted outcome contains the correct job ID, correlation ID, status path, and state.</li>
 *   <li>Persistence failure prevents adapter invocation.</li>
 *   <li>The idempotent path returns the existing job without creating a duplicate.</li>
 * </ul>
 *
 * <p>No Salesforce credentials, Kafka brokers, Kubernetes access, or database infrastructure
 * required — all collaborators are mocked or use test doubles.
 */
@DisplayName("CommandDispatcher — service-boundary integration tests")
class CommandDispatcherIntegrationTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static class CapturingAdapter implements ReleaseCommandAdapter {
        AsyncCommandRequest lastCommand;
        boolean executed;

        @Override
        public String supportedOperationType() { return "QUICK_DEPLOY"; }

        @Override
        public void execute(AsyncCommandRequest command) {
            this.lastCommand = command;
            this.executed = true;
        }
    }

    private static class FailingAdapter implements ReleaseCommandAdapter {
        @Override
        public String supportedOperationType() { return "QUICK_DEPLOY"; }

        @Override
        public void execute(AsyncCommandRequest command) {
            throw new RuntimeException("simulated adapter failure");
        }
    }

    private AsyncCommandRequest deployCommand() {
        return AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .correlationId("corr-integ-test-001")
                .pipelineId("pipeline-integ-fixture-001")
                .stepId("step-integ-fixture-001")
                .deploymentRequestId("deploy-req-integ-fixture-001")
                .customerIdHash("deadbeef00000000deadbeef00000000deadbeef00000000deadbeef00000000")
                .sfdcToolId("sfdc-tool-integ-fixture-001")
                .build();
    }

    // ── AC-6: Service-boundary integration — durable job creation precedes adapter ─

    @Nested
    @DisplayName("AC-6: Job persisted before adapter invocation")
    class JobPersistedBeforeAdapter {

        @Test
        @DisplayName("job record is saved in ACCEPTED state with correct fields before adapter receives command")
        void dispatch_jobSavedBeforeAdapterInvoked() {
            JobRepository repo = mock(JobRepository.class);
            JobLifecycleService lifeSvc = mock(JobLifecycleService.class);
            CapturingAdapter capturingAdapter = new CapturingAdapter();

            DefaultCommandDispatcher dispatcher =
                    new DefaultCommandDispatcher(repo, lifeSvc, capturingAdapter);

            AsyncCommandRequest command = deployCommand();
            AcceptedCommandOutcome outcome = dispatcher.dispatch(command);

            // Verify adapter was invoked
            assertThat(capturingAdapter.executed).isTrue();
            assertThat(capturingAdapter.lastCommand).isSameAs(command);

            // Verify job record was saved with correct fields
            ArgumentCaptor<JobRecord> captor = ArgumentCaptor.forClass(JobRecord.class);
            verify(repo).save(captor.capture());

            JobRecord saved = captor.getValue();
            assertThat(saved.getCurrentState()).isEqualTo(JobLifecycleState.ACCEPTED.name());
            assertThat(saved.getCorrelationId()).isEqualTo("corr-integ-test-001");
            assertThat(saved.getOperationType()).isEqualTo("QUICK_DEPLOY");
            assertThat(saved.getPipelineId()).isEqualTo("pipeline-integ-fixture-001");

            // Verify outcome
            assertThat(outcome.getJobId()).isEqualTo(saved.getJobId());
            assertThat(outcome.getCorrelationId()).isEqualTo("corr-integ-test-001");
            assertThat(outcome.getState()).isEqualTo("ACCEPTED");
            assertThat(outcome.getStatusPath()).startsWith("/api/v2/sfdc/release-jobs/");
        }
    }

    // ── Persistence failure ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Persistence failure prevents adapter invocation")
    class PersistenceFailure {

        @Test
        @DisplayName("repository save failure prevents adapter execution")
        void dispatch_repoSaveFails_adapterNotExecuted() {
            JobRepository repo = mock(JobRepository.class);
            JobLifecycleService lifeSvc = mock(JobLifecycleService.class);
            CapturingAdapter capturingAdapter = new CapturingAdapter();

            doThrow(new com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException("simulated DB failure"))
                    .when(repo).save(any());

            DefaultCommandDispatcher dispatcher =
                    new DefaultCommandDispatcher(repo, lifeSvc, capturingAdapter);

            try {
                dispatcher.dispatch(deployCommand());
            } catch (Exception ignored) {}

            assertThat(capturingAdapter.executed).isFalse();
        }
    }

    // ── Adapter failure marks job failed ─────────────────────────────────────

    @Nested
    @DisplayName("Adapter failure marks job failed")
    class AdapterFailure {

        @Test
        @DisplayName("adapter failure invokes markFailed on lifecycle service with ADAPTER_HANDOFF_FAILURE code")
        void dispatch_adapterFails_lifecycleMarkedFailed() {
            JobRepository repo = mock(JobRepository.class);
            JobLifecycleService lifeSvc = mock(JobLifecycleService.class);

            DefaultCommandDispatcher dispatcher =
                    new DefaultCommandDispatcher(repo, lifeSvc, new FailingAdapter());

            try {
                dispatcher.dispatch(deployCommand());
            } catch (CommandDispatchException e) {
                assertThat(e.getSafeReasonCode()).isEqualTo("ADAPTER_HANDOFF_FAILURE");
                assertThat(e.getJobId()).isNotBlank();
            }

            verify(lifeSvc).markFailed(anyString(), anyString(),
                    anyString(), anyString());
        }
    }

    // ── Idempotent dispatch ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotent dispatch with real dispatcher")
    class IdempotentDispatch {

        @Test
        @DisplayName("idempotency key match returns existing outcome; no new job record or adapter call")
        void dispatch_idempotencyKeyMatch_returnsExisting() {
            JobRepository repo = mock(JobRepository.class);
            JobLifecycleService lifeSvc = mock(JobLifecycleService.class);
            CapturingAdapter capturingAdapter = new CapturingAdapter();

            String idempotencyKey = "idem-key-integ-fixture-001";
            JobRecord existing = new JobRecord();
            existing.setJobId("job-integ-existing-001");
            existing.setCorrelationId("corr-integ-test-001");
            existing.setCurrentState(JobLifecycleState.ACCEPTED.name());
            existing.setOperationType("QUICK_DEPLOY");
            existing.setCreatedAt(java.time.Instant.now());

            when(repo.findByCorrelationId(idempotencyKey)).thenReturn(Optional.of(existing));

            DefaultCommandDispatcher dispatcher =
                    new DefaultCommandDispatcher(repo, lifeSvc, capturingAdapter);

            AsyncCommandRequest command = AsyncCommandRequest.builder()
                    .operationType("QUICK_DEPLOY")
                    .correlationId("corr-integ-test-001")
                    .idempotencyKey(idempotencyKey)
                    .pipelineId("pipeline-integ-fixture-001")
                    .deploymentRequestId("deploy-req-integ-fixture-001")
                    .build();

            AcceptedCommandOutcome outcome = dispatcher.dispatch(command);

            assertThat(outcome.getJobId()).isEqualTo("job-integ-existing-001");
            assertThat(outcome.isIdempotentReplay()).isTrue();
            assertThat(capturingAdapter.executed).isFalse();
            verify(repo, never()).save(any());
        }
    }
}
