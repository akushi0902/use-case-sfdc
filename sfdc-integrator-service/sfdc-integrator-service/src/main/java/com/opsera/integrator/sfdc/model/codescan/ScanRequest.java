package com.opsera.integrator.sfdc.model.codescan;

/**
 * Request DTO for submitting a Salesforce code scan job.
 * repositoryUrl and branch are not logged — only pipelineId and stepId are safe for logs.
 */
public class ScanRequest {

    private String pipelineId;
    private String stepId;
    private String repositoryUrl;
    private String branch;

    public ScanRequest() {}

    public ScanRequest(String pipelineId, String stepId, String repositoryUrl, String branch) {
        this.pipelineId = pipelineId;
        this.stepId = stepId;
        this.repositoryUrl = repositoryUrl;
        this.branch = branch;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
