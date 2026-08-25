package com.opsera.integrator.sfdc.model;

/**
 * Request DTO for legacy deployment and validation operations in SfdcIntegratorController.
 */
public class DeployRequest {

    private String pipelineId;
    private String stepId;
    private String orgUrl;

    public DeployRequest() {}

    public DeployRequest(String pipelineId, String stepId, String orgUrl) {
        this.pipelineId = pipelineId;
        this.stepId = stepId;
        this.orgUrl = orgUrl;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getOrgUrl() { return orgUrl; }
    public void setOrgUrl(String orgUrl) { this.orgUrl = orgUrl; }
}
