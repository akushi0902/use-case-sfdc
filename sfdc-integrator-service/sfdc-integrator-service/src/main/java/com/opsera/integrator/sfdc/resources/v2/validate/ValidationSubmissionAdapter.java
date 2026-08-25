package com.opsera.integrator.sfdc.resources.v2.validate;

import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a validated {@link ValidationSubmissionRequest} to the canonical
 * {@link ReleaseCommandRequest} expected by the v2 release command facade.
 *
 * <p>Task identifier resolution is deterministic:
 * {@code taskId} is always preferred over {@code gitTaskId} when both are supplied.
 *
 * <p>Operation type is always set to {@link ReleaseOperationType#VALIDATE}.
 *
 * <p>Prevalidation warnings are generated when source control, package, or target org
 * context is absent; they are safe strings containing no credentials, tokens, or raw XML.
 */
@Component
public class ValidationSubmissionAdapter {

    /**
     * Maps a validated validation submission request to a canonical release command request.
     *
     * @param request a validated validation submission; must not be null
     * @return a populated {@link ReleaseCommandRequest} with operationType=VALIDATE
     */
    public ReleaseCommandRequest toReleaseCommandRequest(ValidationSubmissionRequest request) {
        ReleaseCommandRequest cmd = new ReleaseCommandRequest();
        cmd.setOperationType(ReleaseOperationType.VALIDATE);
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
     * Generates safe prevalidation warning strings when source control or package context
     * is absent. Warnings are allow-listed strings — no credentials, tokens, or raw XML.
     *
     * @param request the validation submission request
     * @return list of safe warning strings; empty when context is sufficient
     */
    public List<String> collectPrevalidationWarnings(ValidationSubmissionRequest request) {
        List<String> warnings = new ArrayList<>();
        if (request.getRepositoryId() == null || request.getRepositoryId().isBlank()) {
            warnings.add("repositoryId not provided; source control traceability will be limited");
        }
        if (request.getBranch() == null || request.getBranch().isBlank()) {
            warnings.add("branch not provided; validation branch context will not be recorded");
        }
        if (request.getPackageId() == null || request.getPackageId().isBlank()) {
            warnings.add("packageId not provided; component selection will use tool default");
        }
        return warnings;
    }

    /**
     * Resolves the canonical task identifier. {@code taskId} takes precedence;
     * {@code gitTaskId} is the fallback when {@code taskId} is blank or absent.
     */
    String resolveCanonicalTaskId(ValidationSubmissionRequest request) {
        if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
            return request.getTaskId();
        }
        return request.getGitTaskId() != null ? request.getGitTaskId() : "";
    }
}
