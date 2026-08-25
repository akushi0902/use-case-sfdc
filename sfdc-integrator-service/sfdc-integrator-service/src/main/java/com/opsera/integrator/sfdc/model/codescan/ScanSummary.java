package com.opsera.integrator.sfdc.model.codescan;

/**
 * Typed DTO representing the summary result of a completed Salesforce code scan.
 */
public class ScanSummary {

    private String scanId;
    private String pipelineId;
    private int totalViolations;
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private String status;
    private String scannedAt;

    public ScanSummary() {}

    public ScanSummary(String scanId, String pipelineId, int totalViolations,
                        int criticalCount, int highCount, int mediumCount, int lowCount,
                        String status, String scannedAt) {
        this.scanId = scanId;
        this.pipelineId = pipelineId;
        this.totalViolations = totalViolations;
        this.criticalCount = criticalCount;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.status = status;
        this.scannedAt = scannedAt;
    }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public int getTotalViolations() { return totalViolations; }
    public void setTotalViolations(int totalViolations) { this.totalViolations = totalViolations; }

    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }

    public int getHighCount() { return highCount; }
    public void setHighCount(int highCount) { this.highCount = highCount; }

    public int getMediumCount() { return mediumCount; }
    public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }

    public int getLowCount() { return lowCount; }
    public void setLowCount(int lowCount) { this.lowCount = lowCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScannedAt() { return scannedAt; }
    public void setScannedAt(String scannedAt) { this.scannedAt = scannedAt; }
}
