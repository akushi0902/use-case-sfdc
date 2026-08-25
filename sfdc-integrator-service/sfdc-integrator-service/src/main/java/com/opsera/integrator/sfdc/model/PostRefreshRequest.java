package com.opsera.integrator.sfdc.model;

/**
 * Request DTO for legacy post-refresh task operations.
 */
public class PostRefreshRequest {

    private String pipelineId;
    private String stepId;

    public PostRefreshRequest() {}

    public PostRefreshRequest(String pipelineId, String stepId) {
        this.pipelineId = pipelineId;
        this.stepId = stepId;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
}
