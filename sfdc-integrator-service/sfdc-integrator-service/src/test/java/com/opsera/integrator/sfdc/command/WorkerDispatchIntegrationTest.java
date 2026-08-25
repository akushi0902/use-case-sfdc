package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for worker dispatch routing across QUICK_DEPLOY, DEPLOY, VALIDATE, and CANCEL
 * (WO-152 AC-1, AC-2, AC-3, AC-4, AC-5, AC-6).
 *
 * <p>Exercises {@link RoutingReleaseCommandDispatcher} with:
 * <ul>
 *   <li>Worker disabled (default) — traffic routes to legacy service calls</li>
 *   <li>Worker enabled per command type — traffic routes to worker adapter</li>
 *   <li>Worker adapter failure — {@link CommandDispatchException} carries correlationId</li>
 *   <li>Master kill switch off — all traffic falls back to legacy regardless of per-type flags</li>
 * </ul>
 *
 * <p>No Salesforce credentials, Kafka brokers, Kubernetes access, or database infrastructure
 * required — all service collaborators are mocked.
 */
@DisplayName("WorkerDispatch — routing integration tests")
class WorkerDispatchIntegrationTest {

    private WorkerDispatchProperties properties;
    private WorkerReleaseCommandAdapter workerAdapter;
    private QuickDeployService quickDeployService;
    private SfdcIntegratorService sfdcIntegratorService;
    private RoutingReleaseCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new WorkerDispatchProperties();
        workerAdapter = mock(WorkerReleaseCommandAdapter.class);
        quickDeployService = mock(QuickDeployService.class);
        sfdcIntegratorService = mock(SfdcIntegratorService.class);
        dispatcher = new RoutingReleaseCommandDispatcher(
                properties, workerAdapter, quickDeployService, sfdcIntegratorService);
    }

    // ── Worker disabled (default) ─────────────────────────────────────────────

    @Nested
    @DisplayName("Worker disabled — legacy fallback for all command types")
    class WorkerDisabled {

        @Test
        @DisplayName("QUICK_DEPLOY falls back to QuickDeployService.start when worker is disabled")
        void quickDeploy_workerDisabled_usesLegacyService() {
            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("QUICK_DEPLOY"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
            assertThat(result.getCommandType()).isEqualTo("QUICK_DEPLOY");
            assertThat(result.getCorrelationId()).isNotBlank();
            assertThat(result.getJobId()).isNotBlank();
            assertThat(result.getStatusPath()).contains(result.getJobId());
            verify(quickDeployService).start(any(QuickDeployRequest.class));
            verify(workerAdapter, never()).execute(any());
        }

        @Test
        @DisplayName("DEPLOY falls back to SfdcIntegratorService.deploy when worker is disabled")
        void deploy_workerDisabled_usesLegacyService() {
            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("DEPLOY"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
            assertThat(result.getCommandType()).isEqualTo("DEPLOY");
            verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
            verify(workerAdapter, never()).execute(any());
        }

        @Test
        @DisplayName("VALIDATE falls back to SfdcIntegratorService.validate when worker is disabled")
        void validate_workerDisabled_usesLegacyService() {
            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("VALIDATE"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
            assertThat(result.getCommandType()).isEqualTo("VALIDATE");
            verify(sfdcIntegratorService).validate(any(DeployRequest.class));
            verify(workerAdapter, never()).execute(any());
        }
    }

    // ── Worker enabled per command type ───────────────────────────────────────

    @Nested
    @DisplayName("Worker enabled per command type")
    class WorkerEnabled {

        @Test
        @DisplayName("QUICK_DEPLOY routes to worker when master enabled and QUICK_DEPLOY=true")
        void quickDeploy_workerEnabled_routesToWorker() {
            enableWorkerFor("QUICK_DEPLOY");

            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("QUICK_DEPLOY"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
            assertThat(result.wasWorkerDispatched()).isTrue();
            verify(workerAdapter).execute(any(AsyncCommandRequest.class));
            verify(quickDeployService, never()).start(any());
        }

        @Test
        @DisplayName("DEPLOY routes to worker when master enabled and DEPLOY=true")
        void deploy_workerEnabled_routesToWorker() {
            enableWorkerFor("DEPLOY");

            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("DEPLOY"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
            assertThat(result.wasWorkerDispatched()).isTrue();
            verify(workerAdapter).execute(any(AsyncCommandRequest.class));
            verify(sfdcIntegratorService, never()).deploy(any());
        }

        @Test
        @DisplayName("VALIDATE routes to worker when master enabled and VALIDATE=true")
        void validate_workerEnabled_routesToWorker() {
            enableWorkerFor("VALIDATE");

            ReleaseDispatchResult result = dispatcher.dispatch(buildRequest("VALIDATE"));

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
            assertThat(result.wasWorkerDispatched()).isTrue();
            verify(workerAdapter).execute(any(AsyncCommandRequest.class));
            verify(sfdcIntegratorService, never()).validate(any());
        }

        @Test
        @DisplayName("DEPLOY worker command carries correlationId, sfdcToolId, pipelineId, stepId")
        void deploy_workerEnabled_asyncCommandCarriesSafeFields() {
            enableWorkerFor("DEPLOY");

            ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                    .commandType("DEPLOY")
                    .correlationId("corr-deploy-worker-001")
                    .pipelineId("pipeline-worker-deploy-001")
                    .stepId("step-worker-deploy-001")
                    .sfdcToolId("sfdc-tool-worker-001")
                    .deploymentRequestId("deploy-req-worker-001")
                    .build();

            dispatcher.dispatch(request);

            ArgumentCaptor<AsyncCommandRequest> captor = ArgumentCaptor.forClass(AsyncCommandRequest.class);
            verify(workerAdapter).execute(captor.capture());
            AsyncCommandRequest cmd = captor.getValue();
            assertThat(cmd.getOperationType()).isEqualTo("DEPLOY");
            assertThat(cmd.getCorrelationId()).isEqualTo("corr-deploy-worker-001");
            assertThat(cmd.getPipelineId()).isEqualTo("pipeline-worker-deploy-001");
            assertThat(cmd.getStepId()).isEqualTo("step-worker-deploy-001");
            assertThat(cmd.getSfdcToolId()).isEqualTo("sfdc-tool-worker-001");
        }

        @Test
        @DisplayName("VALIDATE worker command carries correlationId, pipelineId, stepId")
        void validate_workerEnabled_asyncCommandCarriesSafeFields() {
            enableWorkerFor("VALIDATE");

            ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                    .commandType("VALIDATE")
                    .correlationId("corr-validate-worker-001")
                    .pipelineId("pipeline-worker-validate-001")
                    .stepId("step-worker-validate-001")
                    .sfdcToolId("sfdc-tool-worker-002")
                    .build();

            dispatcher.dispatch(request);

            ArgumentCaptor<AsyncCommandRequest> captor = ArgumentCaptor.forClass(AsyncCommandRequest.class);
            verify(workerAdapter).execute(captor.capture());
            AsyncCommandRequest cmd = captor.getValue();
            assertThat(cmd.getOperationType()).isEqualTo("VALIDATE");
            assertThat(cmd.getCorrelationId()).isEqualTo("corr-validate-worker-001");
            assertThat(cmd.getPipelineId()).isEqualTo("pipeline-worker-validate-001");
            assertThat(cmd.getStepId()).isEqualTo("step-worker-validate-001");
        }
    }

    // ── Master kill switch ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Master kill switch — disables worker regardless of per-type flags")
    class MasterKillSwitch {

        @Test
        @DisplayName("DEPLOY routes to legacy when master enabled=false and DEPLOY=true")
        void deploy_masterOff_commandTypeTrue_routesToLegacy() {
            properties.setEnabled(false);
            properties.setCommandTypes(Map.of("DEPLOY", true));

            dispatcher.dispatch(buildRequest("DEPLOY"));

            verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
            verify(workerAdapter, never()).execute(any());
        }

        @Test
        @DisplayName("VALIDATE routes to legacy when master enabled=false and VALIDATE=true")
        void validate_masterOff_commandTypeTrue_routesToLegacy() {
            properties.setEnabled(false);
            properties.setCommandTypes(Map.of("VALIDATE", true));

            dispatcher.dispatch(buildRequest("VALIDATE"));

            verify(sfdcIntegratorService).validate(any(DeployRequest.class));
            verify(workerAdapter, never()).execute(any());
        }
    }

    // ── Worker failure behavior ───────────────────────────────────────────────

    @Nested
    @DisplayName("Worker adapter failure — produces CommandDispatchException with correlationId")
    class WorkerFailure {

        @Test
        @DisplayName("DEPLOY worker adapter failure throws CommandDispatchException")
        void deploy_workerAdapterFails_throwsCommandDispatchException() {
            enableWorkerFor("DEPLOY");
            doThrow(new RuntimeException("worker pod unavailable")).when(workerAdapter).execute(any());

            assertThatThrownBy(() -> dispatcher.dispatch(buildRequest("DEPLOY")))
                    .isInstanceOf(CommandDispatchException.class)
                    .satisfies(e -> {
                        CommandDispatchException ex = (CommandDispatchException) e;
                        assertThat(ex.getSafeReasonCode()).isEqualTo("WORKER_UNAVAILABLE");
                        assertThat(ex.getCorrelationId()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("VALIDATE worker adapter failure throws CommandDispatchException")
        void validate_workerAdapterFails_throwsCommandDispatchException() {
            enableWorkerFor("VALIDATE");
            doThrow(new RuntimeException("worker pod evicted")).when(workerAdapter).execute(any());

            assertThatThrownBy(() -> dispatcher.dispatch(buildRequest("VALIDATE")))
                    .isInstanceOf(CommandDispatchException.class)
                    .satisfies(e -> {
                        CommandDispatchException ex = (CommandDispatchException) e;
                        assertThat(ex.getSafeReasonCode()).isEqualTo("WORKER_UNAVAILABLE");
                    });
        }

        @Test
        @DisplayName("QUICK_DEPLOY worker adapter failure throws CommandDispatchException")
        void quickDeploy_workerAdapterFails_throwsCommandDispatchException() {
            enableWorkerFor("QUICK_DEPLOY");
            doThrow(new RuntimeException("worker unavailable")).when(workerAdapter).execute(any());

            assertThatThrownBy(() -> dispatcher.dispatch(buildRequest("QUICK_DEPLOY")))
                    .isInstanceOf(CommandDispatchException.class)
                    .satisfies(e -> {
                        CommandDispatchException ex = (CommandDispatchException) e;
                        assertThat(ex.getSafeReasonCode()).isEqualTo("WORKER_UNAVAILABLE");
                    });
        }
    }

    // ── Cancellation routing ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Cancellation routing")
    class CancellationRouting {

        @Test
        @DisplayName("CANCEL routes to legacy QuickDeployService.stop when worker disabled")
        void cancel_workerDisabled_usesLegacyStop() {
            ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                    .commandType("CANCEL")
                    .correlationId("corr-cancel-worker-001")
                    .pipelineId("pipeline-worker-cancel-001")
                    .deploymentRequestId("deploy-req-worker-cancel-001")
                    .build();

            ReleaseDispatchResult result = dispatcher.cancel(request);

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
            assertThat(result.getCommandType()).isEqualTo("CANCEL");
            verify(quickDeployService).stop(any());
        }

        @Test
        @DisplayName("CANCEL routes to worker when CANCEL flag enabled")
        void cancel_workerEnabled_routesToWorker() {
            enableWorkerFor("CANCEL");

            ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                    .commandType("CANCEL")
                    .correlationId("corr-cancel-worker-001")
                    .pipelineId("pipeline-001")
                    .build();

            ReleaseDispatchResult result = dispatcher.cancel(request);

            assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
            verify(quickDeployService, never()).stop(any());
        }
    }

    // ── Result fields ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("dispatch result carries jobId, correlationId, statusPath, acceptedAt for DEPLOY legacy path")
    void dispatch_legacyDeploy_resultHasRequiredFields() {
        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("DEPLOY")
                .correlationId("corr-deploy-result-001")
                .pipelineId("pipeline-001")
                .stepId("step-001")
                .build();

        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getJobId()).isNotBlank();
        assertThat(result.getCorrelationId()).isEqualTo("corr-deploy-result-001");
        assertThat(result.getStatusPath()).contains(result.getJobId());
        assertThat(result.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("dispatch result carries statusPath with correct pattern for VALIDATE worker path")
    void dispatch_workerValidate_resultHasStatusPath() {
        enableWorkerFor("VALIDATE");

        ReleaseDispatchResult result = dispatcher.dispatch(ReleaseDispatchRequest.builder()
                .commandType("VALIDATE")
                .correlationId("corr-validate-result-001")
                .pipelineId("pipeline-001")
                .stepId("step-001")
                .build());

        assertThat(result.getStatusPath())
                .startsWith("/api/v2/sfdc/release-jobs/")
                .endsWith("/status");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enableWorkerFor(String commandType) {
        properties.setEnabled(true);
        properties.setCommandTypes(Map.of(commandType, true));
    }

    private ReleaseDispatchRequest buildRequest(String commandType) {
        return ReleaseDispatchRequest.builder()
                .commandType(commandType)
                .correlationId("corr-" + commandType.toLowerCase() + "-001")
                .pipelineId("pipeline-worker-001")
                .stepId("step-worker-001")
                .sfdcToolId("sfdc-tool-worker-001")
                .deploymentRequestId("deploy-req-worker-001")
                .build();
    }
}
