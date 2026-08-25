package com.opsera.integrator.sfdc.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the legacy quick deploy start operation.
 *
 * <p>{@code deploymentRequestId} is required; absent or blank values are rejected
 * by Jakarta Bean Validation before controller logic runs. {@code fallbackTaskId}
 * is normalized to an empty string by the controller when the caller omits it.
 */
public class QuickDeployRequest {

    @NotBlank(message = "deploymentRequestId is required")
    private String deploymentRequestId;
    private String pipelineId;
    private String stepId;
    private String fallbackTaskId;

    public QuickDeployRequest() {}

    public QuickDeployRequest(String deploymentRequestId, String pipelineId,
                               String stepId, String fallbackTaskId) {
        this.deploymentRequestId = deploymentRequestId;
        this.pipelineId = pipelineId;
        this.stepId = stepId;
        this.fallbackTaskId = fallbackTaskId;
    }

    public String getDeploymentRequestId() { return deploymentRequestId; }
    public void setDeploymentRequestId(String deploymentRequestId) {
        this.deploymentRequestId = deploymentRequestId;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getFallbackTaskId() { return fallbackTaskId; }
    public void setFallbackTaskId(String fallbackTaskId) { this.fallbackTaskId = fallbackTaskId; }
}
