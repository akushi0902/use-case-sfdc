package com.opsera.integrator.sfdc.model;

/**
 * Request DTO for legacy data migration operations.
 */
public class DataMigrationRequest {

    private String pipelineId;
    private String stepId;
    private String sourceOrgUrl;
    private String targetOrgUrl;

    public DataMigrationRequest() {}

    public DataMigrationRequest(String pipelineId, String stepId,
                                 String sourceOrgUrl, String targetOrgUrl) {
        this.pipelineId = pipelineId;
        this.stepId = stepId;
        this.sourceOrgUrl = sourceOrgUrl;
        this.targetOrgUrl = targetOrgUrl;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getSourceOrgUrl() { return sourceOrgUrl; }
    public void setSourceOrgUrl(String sourceOrgUrl) { this.sourceOrgUrl = sourceOrgUrl; }

    public String getTargetOrgUrl() { return targetOrgUrl; }
    public void setTargetOrgUrl(String targetOrgUrl) { this.targetOrgUrl = targetOrgUrl; }
}
