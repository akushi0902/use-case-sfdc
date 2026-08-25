package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RoutingReleaseCommandDispatcher}.
 *
 * <p>Covers: routing decisions (worker vs legacy), fallback when worker disabled,
 * cancellation routing, request mapping, and null/blank guard behavior.
 */
class RoutingReleaseCommandDispatcherTest {

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

    // ---- Routing decision: worker disabled (default) ----

    @Test
    void dispatch_workerDisabled_quickDeploy_routesToLegacy() {
        ReleaseDispatchRequest request = quickDeployRequest();

        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
        assertThat(result.getCommandType()).isEqualTo("QUICK_DEPLOY");
        assertThat(result.getCorrelationId()).isNotBlank();
        assertThat(result.getJobId()).isNotBlank();
        verify(quickDeployService).start(any(QuickDeployRequest.class));
        verify(workerAdapter, never()).execute(any());
    }

    @Test
    void dispatch_workerDisabled_deploy_routesToLegacy() {
        ReleaseDispatchRequest request = deployRequest();

        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
        assertThat(result.getCommandType()).isEqualTo("DEPLOY");
        verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
        verify(workerAdapter, never()).execute(any());
    }

    @Test
    void dispatch_workerDisabled_validate_routesToLegacy() {
        ReleaseDispatchRequest request = validateRequest();

        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
        assertThat(result.getCommandType()).isEqualTo("VALIDATE");
        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
    }

    // ---- Routing decision: worker enabled ----

    @Test
    void dispatch_workerEnabled_quickDeploy_routesToWorker() {
        enableWorkerFor("QUICK_DEPLOY");

        ReleaseDispatchRequest request = quickDeployRequest();
        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
        assertThat(result.getJobId()).isNotBlank();
        assertThat(result.getCorrelationId()).isEqualTo("corr-qdeploy-001");
        assertThat(result.wasWorkerDispatched()).isTrue();
        verify(workerAdapter).execute(any(AsyncCommandRequest.class));
        verify(quickDeployService, never()).start(any());
    }

    @Test
    void dispatch_workerEnabled_deploy_routesToWorker() {
        enableWorkerFor("DEPLOY");

        ReleaseDispatchResult result = dispatcher.dispatch(deployRequest());

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
        assertThat(result.wasWorkerDispatched()).isTrue();
        verify(workerAdapter).execute(any(AsyncCommandRequest.class));
        verify(sfdcIntegratorService, never()).deploy(any());
    }

    // ---- Worker enabled but master switch off → legacy ----

    @Test
    void dispatch_masterSwitchOff_commandTypeTrue_routesToLegacy() {
        // Command type flag is true but master enabled=false → legacy
        properties.setEnabled(false);
        properties.setCommandTypes(Map.of("QUICK_DEPLOY", true));

        ReleaseDispatchResult result = dispatcher.dispatch(quickDeployRequest());

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
        verify(workerAdapter, never()).execute(any());
    }

    // ---- Request mapping: worker path carries safe fields ----

    @Test
    void dispatch_workerEnabled_asyncCommandRequest_carriesSafeFields() {
        enableWorkerFor("QUICK_DEPLOY");

        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("QUICK_DEPLOY")
                .correlationId("corr-test-001")
                .pipelineId("pipeline-test-001")
                .stepId("step-test-001")
                .sfdcToolId("tool-test-001")
                .deploymentRequestId("deploy-req-001")
                .build();

        dispatcher.dispatch(request);

        // Verify the command passed to workerAdapter carries the safe fields
        org.mockito.ArgumentCaptor<AsyncCommandRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AsyncCommandRequest.class);
        verify(workerAdapter).execute(captor.capture());
        AsyncCommandRequest captured = captor.getValue();
        assertThat(captured.getOperationType()).isEqualTo("QUICK_DEPLOY");
        assertThat(captured.getCorrelationId()).isEqualTo("corr-test-001");
        assertThat(captured.getPipelineId()).isEqualTo("pipeline-test-001");
        assertThat(captured.getStepId()).isEqualTo("step-test-001");
        assertThat(captured.getSfdcToolId()).isEqualTo("tool-test-001");
        assertThat(captured.getDeploymentRequestId()).isEqualTo("deploy-req-001");
    }

    // ---- Cancellation: worker disabled → legacy ----

    @Test
    void cancel_workerDisabled_routesToLegacy() {
        ReleaseDispatchRequest request = cancelRequest();

        ReleaseDispatchResult result = dispatcher.cancel(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_LEGACY);
        assertThat(result.getCommandType()).isEqualTo("CANCEL");
        verify(quickDeployService).stop(any(QuickDeployStopRequest.class));
    }

    @Test
    void cancel_workerEnabled_routesToWorker() {
        enableWorkerFor("CANCEL");

        ReleaseDispatchResult result = dispatcher.cancel(cancelRequest());

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
        assertThat(result.getCommandType()).isEqualTo("CANCEL");
        verify(quickDeployService, never()).stop(any());
    }

    @Test
    void cancel_quickDeployWorkerEnabled_routesCancelToWorker() {
        // If QUICK_DEPLOY worker is enabled, cancel for the same job type goes to worker
        enableWorkerFor("QUICK_DEPLOY");

        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("QUICK_DEPLOY")  // cancel carries the original command type
                .correlationId("corr-cancel-001")
                .pipelineId("pipeline-001")
                .deploymentRequestId("deploy-req-001")
                .build();

        ReleaseDispatchResult result = dispatcher.cancel(request);

        assertThat(result.getRoutedTo()).isEqualTo(ReleaseDispatchResult.ROUTED_TO_WORKER);
        verify(quickDeployService, never()).stop(any());
    }

    // ---- Cancellation carries safe identity fields ----

    @Test
    void cancel_legacyPath_carriesDeploymentRequestIdAndPipelineId() {
        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("CANCEL")
                .correlationId("corr-cancel-002")
                .pipelineId("pipeline-cancel-002")
                .deploymentRequestId("deploy-req-cancel-002")
                .build();

        dispatcher.cancel(request);

        org.mockito.ArgumentCaptor<QuickDeployStopRequest> captor =
                org.mockito.ArgumentCaptor.forClass(QuickDeployStopRequest.class);
        verify(quickDeployService).stop(captor.capture());
        assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-cancel-002");
        assertThat(captor.getValue().getDeploymentRequestId()).isEqualTo("deploy-req-cancel-002");
    }

    // ---- Fallback: unknown command type → CommandDispatchException ----

    @Test
    void dispatch_unknownCommandType_legacyPath_throwsCommandDispatchException() {
        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("UNKNOWN_TYPE")
                .correlationId("corr-unknown-001")
                .pipelineId("pipeline-001")
                .build();

        assertThatThrownBy(() -> dispatcher.dispatch(request))
                .isInstanceOf(CommandDispatchException.class);
    }

    // ---- Null guard ----

    @Test
    void dispatch_nullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_nullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> dispatcher.cancel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Correlation ID generation ----

    @Test
    void dispatch_noCorrelationId_legacyPath_generatesCorrelationId() {
        ReleaseDispatchRequest request = ReleaseDispatchRequest.builder()
                .commandType("QUICK_DEPLOY")
                .pipelineId("pipeline-001")
                .deploymentRequestId("deploy-req-001")
                .build();

        ReleaseDispatchResult result = dispatcher.dispatch(request);

        assertThat(result.getCorrelationId()).isNotBlank();
    }

    // ---- Worker dispatch result carries job ID and correlation ID ----

    @Test
    void dispatch_workerEnabled_resultHasJobIdAndCorrelationId() {
        enableWorkerFor("QUICK_DEPLOY");

        ReleaseDispatchResult result = dispatcher.dispatch(quickDeployRequest());

        assertThat(result.getJobId()).isNotBlank();
        assertThat(result.getCorrelationId()).isEqualTo("corr-qdeploy-001");
        assertThat(result.getStatusPath()).contains(result.getJobId());
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(workerAdapter).execute(any(AsyncCommandRequest.class));
    }

    // ---- Helper methods ----

    private void enableWorkerFor(String commandType) {
        properties.setEnabled(true);
        properties.setCommandTypes(Map.of(commandType, true));
    }

    private ReleaseDispatchRequest quickDeployRequest() {
        return ReleaseDispatchRequest.builder()
                .commandType("QUICK_DEPLOY")
                .correlationId("corr-qdeploy-001")
                .pipelineId("pipeline-001")
                .stepId("step-001")
                .deploymentRequestId("deploy-req-001")
                .build();
    }

    private ReleaseDispatchRequest deployRequest() {
        return ReleaseDispatchRequest.builder()
                .commandType("DEPLOY")
                .correlationId("corr-deploy-001")
                .pipelineId("pipeline-002")
                .stepId("step-002")
                .build();
    }

    private ReleaseDispatchRequest validateRequest() {
        return ReleaseDispatchRequest.builder()
                .commandType("VALIDATE")
                .correlationId("corr-validate-001")
                .pipelineId("pipeline-003")
                .stepId("step-003")
                .build();
    }

    private ReleaseDispatchRequest cancelRequest() {
        return ReleaseDispatchRequest.builder()
                .commandType("CANCEL")
                .correlationId("corr-cancel-001")
                .pipelineId("pipeline-001")
                .deploymentRequestId("deploy-req-001")
                .build();
    }
}
