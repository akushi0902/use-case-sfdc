package com.opsera.integrator.sfdc.model.codescan;

/**
 * Typed DTO representing a quality gate evaluation result for a code scan run.
 */
public class QualityGateResult {

    private String pipelineId;
    private String status;
    private int criticalThreshold;
    private int criticalViolations;
    private boolean passed;
    private String evaluatedAt;

    public QualityGateResult() {}

    public QualityGateResult(String pipelineId, String status, int criticalThreshold,
                              int criticalViolations, boolean passed, String evaluatedAt) {
        this.pipelineId = pipelineId;
        this.status = status;
        this.criticalThreshold = criticalThreshold;
        this.criticalViolations = criticalViolations;
        this.passed = passed;
        this.evaluatedAt = evaluatedAt;
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCriticalThreshold() { return criticalThreshold; }
    public void setCriticalThreshold(int criticalThreshold) { this.criticalThreshold = criticalThreshold; }

    public int getCriticalViolations() { return criticalViolations; }
    public void setCriticalViolations(int criticalViolations) { this.criticalViolations = criticalViolations; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(String evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
