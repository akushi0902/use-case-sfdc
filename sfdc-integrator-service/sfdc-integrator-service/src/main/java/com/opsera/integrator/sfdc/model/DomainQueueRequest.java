package com.opsera.integrator.sfdc.model;

/**
 * Request DTO for legacy domain queue and domain map operations.
 */
public class DomainQueueRequest {

    private String pipelineId;
    private String stepId;
    private String domainName;

    public DomainQueueRequest() {}

    public DomainQueueRequest(String pipelineId, String stepId, String domainName) {
        this.pipelineId = pipelineId;
        this.stepId = stepId;
        this.domainName = domainName;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
}
