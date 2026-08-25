package com.opsera.integrator.sfdc.resources.v2.quickdeploy;

import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import org.springframework.stereotype.Component;

/**
 * Maps a validated {@link QuickDeploySubmissionRequest} to the canonical
 * {@link ReleaseCommandRequest} expected by the v2 release command facade.
 *
 * <p>Task identifier resolution is deterministic:
 * {@code taskId} is always preferred over {@code gitTaskId} when both are supplied.
 * {@code gitTaskId} is only used as the fallback when {@code taskId} is blank or absent.
 *
 * <p>Operation type is always set to {@link ReleaseOperationType#QUICK_DEPLOY} — callers
 * of the dedicated quick-deploy endpoint do not supply the operation type separately.
 */
@Component
public class QuickDeploySubmissionAdapter {

    /**
     * Maps a validated quick deploy submission request to a canonical release command request.
     *
     * @param request a validated quick deploy submission; must not be null
     * @return a populated {@link ReleaseCommandRequest} with operationType=QUICK_DEPLOY
     */
    public ReleaseCommandRequest toReleaseCommandRequest(QuickDeploySubmissionRequest request) {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        cmd.setDeployRequestId(request.getDeployRequestId());
        cmd.setCustomerId(request.getCustomerId());
        cmd.setSfdcToolId(request.getSfdcToolId());
        cmd.setTaskId(resolveCanonicalTaskId(request));
        cmd.setGitTaskId(request.getGitTaskId() != null ? request.getGitTaskId() : "");
        cmd.setPipelineId(request.getPipelineId());
        cmd.setStepId(request.getStepId());
        cmd.setClientCorrelationId(request.getClientCorrelationId());
        return cmd;
    }

    /**
     * Resolves the canonical task identifier. {@code taskId} takes precedence;
     * {@code gitTaskId} is the fallback when {@code taskId} is blank or absent.
     */
    String resolveCanonicalTaskId(QuickDeploySubmissionRequest request) {
        if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
            return request.getTaskId();
        }
        return request.getGitTaskId() != null ? request.getGitTaskId() : "";
    }
}
