package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;

/**
 * Maps legacy release request DTOs to the canonical v2 {@link ReleaseCommandRequest}.
 *
 * <p>Canonical task identifier selection (AC edge case): {@code stepId} is preferred as the
 * primary task identifier; if it is null or blank, {@code fallbackTaskId} is used instead.
 * This mirrors the legacy quick deploy logic where stepId identifies the pipeline step and
 * fallbackTaskId is the secondary identifier.
 */
public class ReleaseCommandMapper {

    /**
     * Maps a legacy {@link QuickDeployRequest} to a v2 command with operationType QUICK_DEPLOY.
     * {@code deploymentRequestId} from the legacy request becomes {@code deployRequestId}.
     * The canonical {@code taskId} is resolved: stepId preferred, fallbackTaskId as fallback.
     */
    public ReleaseCommandRequest fromQuickDeploy(QuickDeployRequest legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("QuickDeployRequest must not be null");
        }
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        cmd.setPipelineId(legacy.getPipelineId());
        cmd.setStepId(legacy.getStepId());
        cmd.setTaskId(resolveTaskId(legacy.getStepId(), legacy.getFallbackTaskId()));
        cmd.setGitTaskId(legacy.getFallbackTaskId());
        cmd.setDeployRequestId(legacy.getDeploymentRequestId());
        return cmd;
    }

    /**
     * Maps a legacy {@link DeployRequest} to a v2 command with operationType DEPLOY.
     * The canonical {@code taskId} is resolved from stepId (no fallback available for deploy).
     */
    public ReleaseCommandRequest fromDeploy(DeployRequest legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("DeployRequest must not be null");
        }
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.DEPLOY);
        cmd.setPipelineId(legacy.getPipelineId());
        cmd.setStepId(legacy.getStepId());
        cmd.setTaskId(resolveTaskId(legacy.getStepId(), null));
        return cmd;
    }

    /**
     * Maps a legacy {@link DeployRequest} to a v2 command with operationType VALIDATE.
     * Shares the same legacy DTO shape as deploy; only the operation type differs.
     */
    public ReleaseCommandRequest fromValidate(DeployRequest legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("DeployRequest must not be null");
        }
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.VALIDATE);
        cmd.setPipelineId(legacy.getPipelineId());
        cmd.setStepId(legacy.getStepId());
        cmd.setTaskId(resolveTaskId(legacy.getStepId(), null));
        return cmd;
    }

    /**
     * Canonical task identifier selection: stepId (primary) is used when non-blank;
     * otherwise fallbackTaskId is used. Returns empty string if both are absent.
     */
    String resolveTaskId(String stepId, String fallbackTaskId) {
        if (stepId != null && !stepId.isBlank()) {
            return stepId;
        }
        if (fallbackTaskId != null && !fallbackTaskId.isBlank()) {
            return fallbackTaskId;
        }
        return "";
    }
}
