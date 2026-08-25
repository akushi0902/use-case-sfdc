package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DeployCommandAdapter} (WO-152 AC-5).
 *
 * <p>Verifies that the adapter correctly maps {@link AsyncCommandRequest} fields to
 * {@link DeployRequest} and delegates to {@link SfdcIntegratorService#deploy}.
 */
@DisplayName("DeployCommandAdapter — unit tests")
class DeployCommandAdapterTest {

    private SfdcIntegratorService sfdcIntegratorService;
    private DeployCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        sfdcIntegratorService = mock(SfdcIntegratorService.class);
        adapter = new DeployCommandAdapter(sfdcIntegratorService);
    }

    @Test
    @DisplayName("supportedOperationType returns DEPLOY")
    void supportedOperationType_returnsDeploy() {
        assertThat(adapter.supportedOperationType()).isEqualTo("DEPLOY");
    }

    @Test
    @DisplayName("execute delegates to SfdcIntegratorService.deploy() with mapped pipelineId and stepId")
    void execute_delegatesToSfdcIntegratorServiceDeploy() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("DEPLOY")
                .correlationId("corr-deploy-worker-001")
                .pipelineId("pipeline-worker-deploy-001")
                .stepId("step-worker-deploy-001")
                .sfdcToolId("sfdc-tool-worker-001")
                .deploymentRequestId("deploy-req-worker-001")
                .build();

        adapter.execute(command);

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService).deploy(captor.capture());

        DeployRequest mapped = captor.getValue();
        assertThat(mapped.getPipelineId()).isEqualTo("pipeline-worker-deploy-001");
        assertThat(mapped.getStepId()).isEqualTo("step-worker-deploy-001");
    }

    @Test
    @DisplayName("execute with null pipelineId — adapter maps null without throwing")
    void execute_nullPipelineId_mapsWithoutThrowing() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("DEPLOY")
                .pipelineId(null)
                .stepId("step-worker-deploy-001")
                .build();

        adapter.execute(command);

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService).deploy(captor.capture());
        assertThat(captor.getValue().getPipelineId()).isNull();
    }

    @Test
    @DisplayName("service exception propagates from adapter — allows dispatcher to catch and mark job failed")
    void execute_serviceException_propagates() {
        doThrow(new RuntimeException("deploy service unavailable")).when(sfdcIntegratorService).deploy(any());

        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("DEPLOY")
                .pipelineId("pipeline-worker-deploy-001")
                .stepId("step-worker-deploy-001")
                .build();

        assertThatThrownBy(() -> adapter.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deploy service unavailable");
    }

    @Test
    @DisplayName("execute does not call SfdcIntegratorService.validate — only deploy")
    void execute_callsDeployNotValidate() {
        adapter.execute(AsyncCommandRequest.builder()
                .operationType("DEPLOY")
                .pipelineId("pipeline-worker-deploy-001")
                .stepId("step-worker-deploy-001")
                .build());

        verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
        org.mockito.Mockito.verify(sfdcIntegratorService, org.mockito.Mockito.never()).validate(any());
    }
}
