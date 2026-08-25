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
 * Unit tests for {@link ValidateCommandAdapter} (WO-152 AC-5).
 *
 * <p>Verifies that the adapter correctly maps {@link AsyncCommandRequest} fields to
 * {@link DeployRequest} and delegates to {@link SfdcIntegratorService#validate}.
 */
@DisplayName("ValidateCommandAdapter — unit tests")
class ValidateCommandAdapterTest {

    private SfdcIntegratorService sfdcIntegratorService;
    private ValidateCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        sfdcIntegratorService = mock(SfdcIntegratorService.class);
        adapter = new ValidateCommandAdapter(sfdcIntegratorService);
    }

    @Test
    @DisplayName("supportedOperationType returns VALIDATE")
    void supportedOperationType_returnsValidate() {
        assertThat(adapter.supportedOperationType()).isEqualTo("VALIDATE");
    }

    @Test
    @DisplayName("execute delegates to SfdcIntegratorService.validate() with mapped pipelineId and stepId")
    void execute_delegatesToSfdcIntegratorServiceValidate() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("VALIDATE")
                .correlationId("corr-validate-worker-001")
                .pipelineId("pipeline-worker-validate-001")
                .stepId("step-worker-validate-001")
                .sfdcToolId("sfdc-tool-worker-002")
                .build();

        adapter.execute(command);

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService).validate(captor.capture());

        DeployRequest mapped = captor.getValue();
        assertThat(mapped.getPipelineId()).isEqualTo("pipeline-worker-validate-001");
        assertThat(mapped.getStepId()).isEqualTo("step-worker-validate-001");
    }

    @Test
    @DisplayName("execute with null stepId — adapter maps null without throwing")
    void execute_nullStepId_mapsWithoutThrowing() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("VALIDATE")
                .pipelineId("pipeline-worker-validate-001")
                .stepId(null)
                .build();

        adapter.execute(command);

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService).validate(captor.capture());
        assertThat(captor.getValue().getStepId()).isNull();
    }

    @Test
    @DisplayName("service exception propagates from adapter — allows dispatcher to catch and mark job failed")
    void execute_serviceException_propagates() {
        doThrow(new RuntimeException("validate service unavailable")).when(sfdcIntegratorService).validate(any());

        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("VALIDATE")
                .pipelineId("pipeline-worker-validate-001")
                .stepId("step-worker-validate-001")
                .build();

        assertThatThrownBy(() -> adapter.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("validate service unavailable");
    }

    @Test
    @DisplayName("execute calls SfdcIntegratorService.validate — not deploy")
    void execute_callsValidateNotDeploy() {
        adapter.execute(AsyncCommandRequest.builder()
                .operationType("VALIDATE")
                .pipelineId("pipeline-worker-validate-001")
                .stepId("step-worker-validate-001")
                .build());

        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
        org.mockito.Mockito.verify(sfdcIntegratorService, org.mockito.Mockito.never()).deploy(any());
    }
}
