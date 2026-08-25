package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
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
 * Unit tests for {@link QuickDeployCommandAdapter}.
 */
@DisplayName("QuickDeployCommandAdapter — unit tests")
class QuickDeployCommandAdapterTest {

    private QuickDeployService quickDeployService;
    private QuickDeployCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        quickDeployService = mock(QuickDeployService.class);
        adapter = new QuickDeployCommandAdapter(quickDeployService);
    }

    @Test
    @DisplayName("supportedOperationType returns QUICK_DEPLOY")
    void supportedOperationType_returnsQuickDeploy() {
        assertThat(adapter.supportedOperationType()).isEqualTo("QUICK_DEPLOY");
    }

    @Test
    @DisplayName("execute delegates to QuickDeployService.start() with mapped request fields")
    void execute_delegatesToQuickDeployService() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .correlationId("corr-adapter-test-001")
                .pipelineId("pipeline-adapter-fixture-001")
                .stepId("step-adapter-fixture-001")
                .deploymentRequestId("deploy-req-adapter-fixture-001")
                .build();

        adapter.execute(command);

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService).start(captor.capture());

        QuickDeployRequest mapped = captor.getValue();
        assertThat(mapped.getDeploymentRequestId()).isEqualTo("deploy-req-adapter-fixture-001");
        assertThat(mapped.getPipelineId()).isEqualTo("pipeline-adapter-fixture-001");
        assertThat(mapped.getStepId()).isEqualTo("step-adapter-fixture-001");
        assertThat(mapped.getFallbackTaskId()).isEqualTo(""); // normalized to empty string
    }

    @Test
    @DisplayName("null deploymentRequestId is normalized to empty string — adapter never passes null to service")
    void execute_nullDeploymentRequestId_normalizedToEmpty() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .pipelineId("pipeline-adapter-fixture-002")
                .deploymentRequestId(null)
                .build();

        adapter.execute(command);

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService).start(captor.capture());
        assertThat(captor.getValue().getDeploymentRequestId()).isEqualTo("");
    }

    @Test
    @DisplayName("service exception propagates from adapter — allows dispatcher to catch and mark job failed")
    void execute_serviceException_propagates() {
        doThrow(new RuntimeException("service unavailable")).when(quickDeployService).start(any());

        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .deploymentRequestId("deploy-req-adapter-fixture-003")
                .build();

        assertThatThrownBy(() -> adapter.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("service unavailable");
    }
}
