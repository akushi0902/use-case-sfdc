package com.opsera.integrator.sfdc.model.orghealth;

/**
 * Request DTO for triggering an org health data collection run.
 */
public class OrgHealthCollectRequest {

    private String customerId;
    private String toolId;
    private String orgUrl;
    private String apiVersion;

    public OrgHealthCollectRequest() {}

    public OrgHealthCollectRequest(String customerId, String toolId, String orgUrl, String apiVersion) {
        this.customerId = customerId;
        this.toolId = toolId;
        this.orgUrl = orgUrl;
        this.apiVersion = apiVersion;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public String getOrgUrl() { return orgUrl; }
    public void setOrgUrl(String orgUrl) { this.orgUrl = orgUrl; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
}
