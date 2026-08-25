package com.opsera.integrator.sfdc.resources.v2.release;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReleaseCommandMapper} covering task identifier selection,
 * operation type assignment, and legacy DTO field mapping (AC-1).
 */
class ReleaseCommandMapperTest {

    private ReleaseCommandMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ReleaseCommandMapper();
    }

    // ---- fromQuickDeploy ----

    @Test
    void fromQuickDeploy_setsOperationTypeQuickDeploy() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", "step-1", null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals(ReleaseOperationType.QUICK_DEPLOY, cmd.getOperationType());
    }

    @Test
    void fromQuickDeploy_prefersStepIdAsTaskId() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", "step-primary", "fallback-task");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("step-primary", cmd.getTaskId(), "stepId must be preferred over fallbackTaskId");
    }

    @Test
    void fromQuickDeploy_useFallbackTaskIdWhenStepIdIsNull() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", null, "fallback-task");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("fallback-task", cmd.getTaskId(), "fallbackTaskId must be used when stepId is null");
    }

    @Test
    void fromQuickDeploy_useFallbackTaskIdWhenStepIdIsBlank() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", "  ", "fallback-task");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("fallback-task", cmd.getTaskId(), "fallbackTaskId must be used when stepId is blank");
    }

    @Test
    void fromQuickDeploy_returnsEmptyTaskIdWhenBothAbsent() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", null, null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("", cmd.getTaskId(), "taskId must be empty string when both stepId and fallbackTaskId are absent");
    }

    @Test
    void fromQuickDeploy_mapsDeploymentRequestId() {
        QuickDeployRequest legacy = new QuickDeployRequest("deploy-req-99", "pipe-1", "step-1", null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("deploy-req-99", cmd.getDeployRequestId());
    }

    @Test
    void fromQuickDeploy_mapsPipelineId() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipeline-abc", "step-1", null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("pipeline-abc", cmd.getPipelineId());
    }

    @Test
    void fromQuickDeploy_setsGitTaskIdToFallbackTaskId() {
        QuickDeployRequest legacy = new QuickDeployRequest("req-1", "pipe-1", "step-1", "git-task-5");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(legacy);
        assertEquals("git-task-5", cmd.getGitTaskId());
    }

    @Test
    void fromQuickDeploy_throwsOnNullInput() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromQuickDeploy(null));
    }

    // ---- fromDeploy ----

    @Test
    void fromDeploy_setsOperationTypeDeploy() {
        DeployRequest legacy = new DeployRequest("pipe-1", "step-1", "https://example.internal");
        ReleaseCommandRequest cmd = mapper.fromDeploy(legacy);
        assertEquals(ReleaseOperationType.DEPLOY, cmd.getOperationType());
    }

    @Test
    void fromDeploy_usesStepIdAsTaskId() {
        DeployRequest legacy = new DeployRequest("pipe-1", "step-42", "https://example.internal");
        ReleaseCommandRequest cmd = mapper.fromDeploy(legacy);
        assertEquals("step-42", cmd.getTaskId());
    }

    @Test
    void fromDeploy_mapsPipelineId() {
        DeployRequest legacy = new DeployRequest("pipeline-xyz", "step-1", "https://example.internal");
        ReleaseCommandRequest cmd = mapper.fromDeploy(legacy);
        assertEquals("pipeline-xyz", cmd.getPipelineId());
    }

    @Test
    void fromDeploy_throwsOnNullInput() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromDeploy(null));
    }

    // ---- fromValidate ----

    @Test
    void fromValidate_setsOperationTypeValidate() {
        DeployRequest legacy = new DeployRequest("pipe-1", "step-1", "https://example.internal");
        ReleaseCommandRequest cmd = mapper.fromValidate(legacy);
        assertEquals(ReleaseOperationType.VALIDATE, cmd.getOperationType());
    }

    @Test
    void fromValidate_usesStepIdAsTaskId() {
        DeployRequest legacy = new DeployRequest("pipe-1", "step-validate", "https://example.internal");
        ReleaseCommandRequest cmd = mapper.fromValidate(legacy);
        assertEquals("step-validate", cmd.getTaskId());
    }

    @Test
    void fromValidate_throwsOnNullInput() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromValidate(null));
    }

    // ---- resolveTaskId ----

    @Test
    void resolveTaskId_prefersStepId() {
        assertEquals("step", mapper.resolveTaskId("step", "fallback"));
    }

    @Test
    void resolveTaskId_fallsBackWhenStepIdNull() {
        assertEquals("fallback", mapper.resolveTaskId(null, "fallback"));
    }

    @Test
    void resolveTaskId_fallsBackWhenStepIdBlank() {
        assertEquals("fallback", mapper.resolveTaskId("   ", "fallback"));
    }

    @Test
    void resolveTaskId_returnsEmptyWhenBothNull() {
        assertEquals("", mapper.resolveTaskId(null, null));
    }

    @Test
    void resolveTaskId_returnsEmptyWhenBothBlank() {
        assertEquals("", mapper.resolveTaskId("", "  "));
    }
}
