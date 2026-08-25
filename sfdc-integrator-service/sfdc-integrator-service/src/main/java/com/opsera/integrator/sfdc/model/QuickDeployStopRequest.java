package com.opsera.integrator.sfdc.model;

/**
 * Request DTO for the legacy quick deploy cancellation operation.
 */
public class QuickDeployStopRequest {

    private String deploymentRequestId;
    private String pipelineId;

    public QuickDeployStopRequest() {}

    public QuickDeployStopRequest(String deploymentRequestId, String pipelineId) {
        this.deploymentRequestId = deploymentRequestId;
        this.pipelineId = pipelineId;
    }

    public String getDeploymentRequestId() { return deploymentRequestId; }
    public void setDeploymentRequestId(String deploymentRequestId) {
        this.deploymentRequestId = deploymentRequestId;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
}
