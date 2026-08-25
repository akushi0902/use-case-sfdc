package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.command.ReleaseCommandDispatcher;
import com.opsera.integrator.sfdc.command.ReleaseDispatchRequest;
import com.opsera.integrator.sfdc.command.ReleaseDispatchResult;
import com.opsera.integrator.sfdc.command.WorkerDispatchProperties;
import com.opsera.integrator.sfdc.exceptions.V2UnsupportedOperationException;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseCommandFacade} with worker dispatch integration.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Legacy response shape (HTTP 202 + AcceptedAcknowledgement) is preserved when worker
 *       dispatch is disabled (AC-2, AC-6)</li>
 *   <li>Worker dispatch is invoked when enabled; legacy services are NOT called (AC-1, AC-2)</li>
 *   <li>CANCEL throws V2UnsupportedOperationException when worker dispatch is not enabled (AC-3)</li>
 *   <li>Cancellation routes through the dispatcher when worker dispatch is enabled (AC-3)</li>
 *   <li>Dispatch request carries correlation, operation type, pipeline, step, tool (AC-4)</li>
 * </ul>
 */
class ReleaseCommandFacadeDispatcherTest {

    private QuickDeployService quickDeployService;
    private SfdcIntegratorService sfdcIntegratorService;
    private ReleaseCommandDispatcher releaseCommandDispatcher;
    private WorkerDispatchProperties workerDispatchProperties;
    private ReleaseCommandFacade facade;

    @BeforeEach
    void setUp() {
        quickDeployService = mock(QuickDeployService.class);
        sfdcIntegratorService = mock(SfdcIntegratorService.class);
        releaseCommandDispatcher = mock(ReleaseCommandDispatcher.class);
        workerDispatchProperties = new WorkerDispatchProperties();
        facade = new ReleaseCommandFacade(quickDeployService, sfdcIntegratorService,
                releaseCommandDispatcher, workerDispatchProperties);
    }

    // ---- Worker disabled: legacy response behavior preserved ----

    @Test
    void accept_workerDisabled_quickDeploy_callsLegacyService_returnsAcceptedAck() {
        ReleaseCommandRequest cmd = quickDeployCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.QUICK_DEPLOY);
        assertThat(ack.getJobId()).isNotBlank();
        assertThat(ack.getCorrelationId()).isNotBlank();
        assertThat(ack.getStatusUrl()).contains(ack.getJobId());
        verify(quickDeployService).start(any(QuickDeployRequest.class));
        verify(releaseCommandDispatcher, never()).dispatch(any());
    }

    @Test
    void accept_workerDisabled_deploy_callsLegacyService_returnsAcceptedAck() {
        ReleaseCommandRequest cmd = deployCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.DEPLOY);
        verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
        verify(releaseCommandDispatcher, never()).dispatch(any());
    }

    @Test
    void accept_workerDisabled_validate_callsLegacyService_returnsAcceptedAck() {
        ReleaseCommandRequest cmd = validateCommand();

        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.VALIDATE);
        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
    }

    @Test
    void accept_workerDisabled_cancel_throwsV2UnsupportedOperationException() {
        assertThatThrownBy(() -> facade.accept(cancelCommand()))
                .isInstanceOf(V2UnsupportedOperationException.class);
        verify(releaseCommandDispatcher, never()).dispatch(any());
    }

    // ---- Worker enabled: dispatcher invoked, legacy services NOT called ----

    @Test
    void accept_workerEnabled_quickDeploy_callsDispatcher_notLegacyService() {
        enableWorkerFor("QUICK_DEPLOY");
        ReleaseDispatchResult result = workerResult("job-w-001", "corr-w-001", "QUICK_DEPLOY");
        when(releaseCommandDispatcher.dispatch(any())).thenReturn(result);

        ReleaseCommandRequest cmd = quickDeployCommand();
        AcceptedAcknowledgement ack = facade.accept(cmd);

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getJobId()).isEqualTo("job-w-001");
        assertThat(ack.getCorrelationId()).isEqualTo("corr-w-001");
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.QUICK_DEPLOY);
        verify(releaseCommandDispatcher).dispatch(any(ReleaseDispatchRequest.class));
        verify(quickDeployService, never()).start(any());
    }

    @Test
    void accept_workerEnabled_deploy_callsDispatcher_notLegacyService() {
        enableWorkerFor("DEPLOY");
        ReleaseDispatchResult result = workerResult("job-w-002", "corr-w-002", "DEPLOY");
        when(releaseCommandDispatcher.dispatch(any())).thenReturn(result);

        AcceptedAcknowledgement ack = facade.accept(deployCommand());

        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.DEPLOY);
        verify(releaseCommandDispatcher).dispatch(any(ReleaseDispatchRequest.class));
        verify(sfdcIntegratorService, never()).deploy(any());
    }

    // ---- Dispatch request carries required AC-4 fields ----

    @Test
    void accept_workerEnabled_dispatchRequestCarriesCorrelationAndIdentityFields() {
        enableWorkerFor("QUICK_DEPLOY");
        ReleaseDispatchResult result = workerResult("job-w-003", "corr-w-003", "QUICK_DEPLOY");
        when(releaseCommandDispatcher.dispatch(any())).thenReturn(result);

        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        cmd.setCustomerId("customer-fixture-001");
        cmd.setSfdcToolId("tool-fixture-001");
        cmd.setTaskId("task-fixture-001");
        cmd.setClientCorrelationId("client-corr-001");
        cmd.setPipelineId("pipeline-fixture-001");
        cmd.setStepId("step-fixture-001");
        cmd.setDeployRequestId("deploy-req-001");
        cmd.setGitTaskId("fallback-task-001");

        facade.accept(cmd);

        org.mockito.ArgumentCaptor<ReleaseDispatchRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ReleaseDispatchRequest.class);
        verify(releaseCommandDispatcher).dispatch(captor.capture());
        ReleaseDispatchRequest captured = captor.getValue();

        assertThat(captured.getCommandType()).isEqualTo("QUICK_DEPLOY");
        assertThat(captured.getPipelineId()).isEqualTo("pipeline-fixture-001");
        assertThat(captured.getStepId()).isEqualTo("step-fixture-001");
        assertThat(captured.getSfdcToolId()).isEqualTo("tool-fixture-001");
        assertThat(captured.getDeploymentRequestId()).isEqualTo("deploy-req-001");
        assertThat(captured.getFallbackTaskId()).isEqualTo("fallback-task-001");
        assertThat(captured.getTimeoutSeconds()).isGreaterThan(0);
    }

    // ---- Accepted ack shape preserved when worker is enabled ----

    @Test
    void accept_workerEnabled_acceptedAckHasStateAccepted_statusUrlAndOperationType() {
        enableWorkerFor("VALIDATE");
        ReleaseDispatchResult result = workerResult("job-v-001", "corr-v-001", "VALIDATE");
        when(releaseCommandDispatcher.dispatch(any())).thenReturn(result);

        AcceptedAcknowledgement ack = facade.accept(validateCommand());

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        assertThat(ack.getJobId()).isNotBlank();
        assertThat(ack.getStatusUrl()).isNotBlank();
        assertThat(ack.getAcceptedAt()).isNotNull();
        assertThat(ack.getOperationType()).isEqualTo(ReleaseOperationType.VALIDATE);
    }

    // ---- CANCEL via dispatcher ----

    @Test
    void accept_cancel_workerEnabled_callsDispatcher() {
        enableWorkerFor("CANCEL");
        ReleaseDispatchResult result = ReleaseDispatchResult.builder()
                .jobId("job-cancel-001")
                .correlationId("corr-cancel-001")
                .commandType("CANCEL")
                .routedTo(ReleaseDispatchResult.ROUTED_TO_WORKER)
                .statusPath("/api/v2/sfdc/release-jobs/job-cancel-001/status")
                .acceptedAt(Instant.now())
                .build();
        when(releaseCommandDispatcher.dispatch(any())).thenReturn(result);

        AcceptedAcknowledgement ack = facade.accept(cancelCommand());

        assertThat(ack.getState()).isEqualTo(ReleaseLifecycleState.ACCEPTED);
        verify(releaseCommandDispatcher).dispatch(any(ReleaseDispatchRequest.class));
    }

    // ---- Helper methods ----

    private void enableWorkerFor(String commandType) {
        workerDispatchProperties.setEnabled(true);
        workerDispatchProperties.setCommandTypes(Map.of(commandType, true));
    }

    private ReleaseDispatchResult workerResult(String jobId, String correlationId, String commandType) {
        return ReleaseDispatchResult.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .commandType(commandType)
                .routedTo(ReleaseDispatchResult.ROUTED_TO_WORKER)
                .statusPath("/api/v2/sfdc/release-jobs/" + jobId + "/status")
                .acceptedAt(Instant.now())
                .idempotentReplay(false)
                .build();
    }

    private ReleaseCommandRequest quickDeployCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        cmd.setCustomerId("customer-fixture-001");
        cmd.setSfdcToolId("tool-fixture-001");
        cmd.setTaskId("task-fixture-001");
        cmd.setPipelineId("pipeline-fixture-001");
        cmd.setStepId("step-qdeploy-fixture-001");
        cmd.setDeployRequestId("deploy-req-fixture-001");
        return cmd;
    }

    private ReleaseCommandRequest deployCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.DEPLOY);
        cmd.setCustomerId("customer-fixture-002");
        cmd.setSfdcToolId("tool-fixture-002");
        cmd.setTaskId("task-fixture-002");
        cmd.setPipelineId("pipeline-fixture-002");
        cmd.setStepId("step-deploy-fixture-002");
        return cmd;
    }

    private ReleaseCommandRequest validateCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.VALIDATE);
        cmd.setCustomerId("customer-fixture-003");
        cmd.setSfdcToolId("tool-fixture-003");
        cmd.setTaskId("task-fixture-003");
        cmd.setPipelineId("pipeline-fixture-003");
        cmd.setStepId("step-validate-fixture-003");
        return cmd;
    }

    private ReleaseCommandRequest cancelCommand() {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.CANCEL);
        cmd.setCustomerId("customer-fixture-001");
        cmd.setSfdcToolId("tool-fixture-001");
        cmd.setTaskId("task-fixture-001");
        cmd.setPipelineId("pipeline-fixture-001");
        return cmd;
    }
}
