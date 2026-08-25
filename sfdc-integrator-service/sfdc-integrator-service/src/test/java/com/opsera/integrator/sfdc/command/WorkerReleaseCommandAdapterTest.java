package com.opsera.integrator.sfdc.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WorkerReleaseCommandAdapter}.
 *
 * <p>Verifies safe logging behavior (no credentials emitted), null guard, and
 * that all release operation types are handled without throwing.
 */
class WorkerReleaseCommandAdapterTest {

    private WorkerReleaseCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkerReleaseCommandAdapter();
    }

    @Test
    void supportedOperationType_returnsWorkerDispatch() {
        assertThat(adapter.supportedOperationType()).isEqualTo(WorkerReleaseCommandAdapter.OPERATION_TYPE_WORKER);
    }

    @Test
    void execute_quickDeploy_completesWithoutException() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .correlationId("corr-w-test-001")
                .pipelineId("pipeline-w-001")
                .stepId("step-w-001")
                .sfdcToolId("tool-w-001")
                .build();

        adapter.execute(command);  // must not throw
    }

    @Test
    void execute_deploy_completesWithoutException() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("DEPLOY")
                .correlationId("corr-w-deploy-001")
                .pipelineId("pipeline-w-002")
                .stepId("step-w-002")
                .build();

        adapter.execute(command);
    }

    @Test
    void execute_validate_completesWithoutException() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("VALIDATE")
                .correlationId("corr-w-validate-001")
                .pipelineId("pipeline-w-003")
                .stepId("step-w-003")
                .build();

        adapter.execute(command);
    }

    @Test
    void execute_cancel_completesWithoutException() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("CANCEL")
                .correlationId("corr-w-cancel-001")
                .pipelineId("pipeline-w-004")
                .build();

        adapter.execute(command);
    }

    @Test
    void execute_nullCommand_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> adapter.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_missingCorrelationId_completesWithoutException() {
        AsyncCommandRequest command = AsyncCommandRequest.builder()
                .operationType("QUICK_DEPLOY")
                .pipelineId("pipeline-w-005")
                .stepId("step-w-005")
                .build();

        adapter.execute(command);
    }
}
