package com.opsera.integrator.sfdc.model.orghealth;

/**
 * Typed response DTO for cached org health report data.
 */
public class OrgHealthResponse {

    private String customerId;
    private String toolId;
    private String orgId;
    private String apiVersion;
    private String overallStatus;
    private int healthScore;
    private String collectedAt;

    public OrgHealthResponse() {}

    public OrgHealthResponse(String customerId, String toolId, String orgId,
                              String apiVersion, String overallStatus,
                              int healthScore, String collectedAt) {
        this.customerId = customerId;
        this.toolId = toolId;
        this.orgId = orgId;
        this.apiVersion = apiVersion;
        this.overallStatus = overallStatus;
        this.healthScore = healthScore;
        this.collectedAt = collectedAt;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }

    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }

    public String getCollectedAt() { return collectedAt; }
    public void setCollectedAt(String collectedAt) { this.collectedAt = collectedAt; }
}
