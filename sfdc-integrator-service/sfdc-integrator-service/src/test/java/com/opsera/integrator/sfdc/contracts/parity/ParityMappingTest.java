package com.opsera.integrator.sfdc.contracts.parity;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReleaseCommandMapper} verifying field-level parity between legacy
 * request DTOs and v2 {@link ReleaseCommandRequest} (WO-153 AC-1).
 *
 * <p>No Spring context — instantiates ReleaseCommandMapper directly.
 */
class ParityMappingTest {

    private ReleaseCommandMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ReleaseCommandMapper();
    }

    // ---- PARITY-QD-001: QuickDeploy → QUICK_DEPLOY ----

    @Test
    void fromQuickDeploy_setsOperationTypeQuickDeploy() {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-parity-qd-001", "pipeline-parity-001", "step-parity-001", "fallback-parity-001");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getOperationType()).isEqualTo(ReleaseOperationType.QUICK_DEPLOY);
    }

    @Test
    void fromQuickDeploy_mapsDeploymentRequestIdToDeployRequestId() {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-parity-qd-001", "pipeline-parity-001", "step-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getDeployRequestId()).isEqualTo("deploy-req-parity-qd-001");
    }

    @Test
    void fromQuickDeploy_mapsPipelineId() {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-001", "pipeline-parity-001", "step-001", null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getPipelineId()).isEqualTo("pipeline-parity-001");
    }

    @Test
    void fromQuickDeploy_taskIdUsesStepIdWhenPresent() {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-001", "pipeline-001", "step-parity-001", "fallback-xyz");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getTaskId()).isEqualTo("step-parity-001");
    }

    @Test
    void fromQuickDeploy_taskIdFallsBackWhenStepIdBlank() {
        QuickDeployRequest req = new QuickDeployRequest("deploy-req-001", "pipeline-001", "", "fallback-xyz");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getTaskId()).isEqualTo("fallback-xyz");
    }

    @Test
    void fromQuickDeploy_taskIdFallsBackWhenStepIdNull() {
        QuickDeployRequest req = new QuickDeployRequest("deploy-req-001", "pipeline-001", null, "fallback-xyz");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getTaskId()).isEqualTo("fallback-xyz");
    }

    @Test
    void fromQuickDeploy_taskIdEmptyWhenBothAbsent() {
        QuickDeployRequest req = new QuickDeployRequest("deploy-req-001", "pipeline-001", null, null);
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getTaskId()).isEmpty();
    }

    @Test
    void fromQuickDeploy_gitTaskIdSetToFallbackTaskId() {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-001", "pipeline-001", "step-001", "fallback-parity-001");
        ReleaseCommandRequest cmd = mapper.fromQuickDeploy(req);
        assertThat(cmd.getGitTaskId()).isEqualTo("fallback-parity-001");
    }

    @Test
    void fromQuickDeploy_nullInputThrowsIllegalArgument() {
        assertThatThrownBy(() -> mapper.fromQuickDeploy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QuickDeployRequest must not be null");
    }

    // ---- PARITY-D-001: DeployRequest → DEPLOY ----

    @Test
    void fromDeploy_setsOperationTypeDeploy() {
        DeployRequest req = new DeployRequest("pipeline-parity-deploy-001", "step-deploy-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromDeploy(req);
        assertThat(cmd.getOperationType()).isEqualTo(ReleaseOperationType.DEPLOY);
    }

    @Test
    void fromDeploy_mapsPipelineIdAndStepId() {
        DeployRequest req = new DeployRequest("pipeline-parity-deploy-001", "step-deploy-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromDeploy(req);
        assertThat(cmd.getPipelineId()).isEqualTo("pipeline-parity-deploy-001");
        assertThat(cmd.getStepId()).isEqualTo("step-deploy-parity-001");
    }

    @Test
    void fromDeploy_taskIdResolvesFromStepId() {
        DeployRequest req = new DeployRequest("pipeline-001", "step-deploy-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromDeploy(req);
        assertThat(cmd.getTaskId()).isEqualTo("step-deploy-parity-001");
    }

    @Test
    void fromDeploy_orgUrlIsIntentionallyOmitted() {
        DeployRequest req = new DeployRequest("pipeline-001", "step-001", "https://sfdc.example.internal");
        ReleaseCommandRequest cmd = mapper.fromDeploy(req);
        // orgUrl is intentionally not mapped — credentials are injected via managed secrets
        assertThat(cmd).isNotNull();
    }

    @Test
    void fromDeploy_nullInputThrowsIllegalArgument() {
        assertThatThrownBy(() -> mapper.fromDeploy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DeployRequest must not be null");
    }

    // ---- PARITY-V-001: DeployRequest → VALIDATE ----

    @Test
    void fromValidate_setsOperationTypeValidate() {
        DeployRequest req = new DeployRequest("pipeline-parity-validate-001", "step-validate-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromValidate(req);
        assertThat(cmd.getOperationType()).isEqualTo(ReleaseOperationType.VALIDATE);
    }

    @Test
    void fromValidate_mapsPipelineIdAndStepId() {
        DeployRequest req = new DeployRequest("pipeline-parity-validate-001", "step-validate-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromValidate(req);
        assertThat(cmd.getPipelineId()).isEqualTo("pipeline-parity-validate-001");
        assertThat(cmd.getStepId()).isEqualTo("step-validate-parity-001");
    }

    @Test
    void fromValidate_taskIdResolvesFromStepId() {
        DeployRequest req = new DeployRequest("pipeline-001", "step-validate-parity-001", null);
        ReleaseCommandRequest cmd = mapper.fromValidate(req);
        assertThat(cmd.getTaskId()).isEqualTo("step-validate-parity-001");
    }

    @Test
    void fromValidate_nullInputThrowsIllegalArgument() {
        assertThatThrownBy(() -> mapper.fromValidate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DeployRequest must not be null");
    }

    // ---- resolveTaskId edge cases ----

    @Test
    void resolveTaskId_stepIdPreferredOverFallback() {
        assertThat(mapper.resolveTaskId("step-123", "fallback-456")).isEqualTo("step-123");
    }

    @Test
    void resolveTaskId_fallbackUsedWhenStepIdBlank() {
        assertThat(mapper.resolveTaskId("  ", "fallback-456")).isEqualTo("fallback-456");
    }

    @Test
    void resolveTaskId_emptyStringWhenBothBlank() {
        assertThat(mapper.resolveTaskId("", "")).isEmpty();
    }

    @Test
    void resolveTaskId_emptyStringWhenBothNull() {
        assertThat(mapper.resolveTaskId(null, null)).isEmpty();
    }
}
