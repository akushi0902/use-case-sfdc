package com.opsera.integrator.sfdc.resources.v2.release;

import java.time.Instant;

/**
 * V2 lightweight status reference returned from status-polling endpoints.
 * Contains only the fields needed for a UI or orchestrator to track release progress.
 */
public class ReleaseStatusReference {

    private String jobId;
    private String correlationId;
    private ReleaseLifecycleState state;
    private ReleaseOperationType operationType;
    private Instant lastUpdatedAt;
    private String statusMessage;

    public ReleaseStatusReference() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public ReleaseLifecycleState getState() { return state; }
    public void setState(ReleaseLifecycleState state) { this.state = state; }

    public ReleaseOperationType getOperationType() { return operationType; }
    public void setOperationType(ReleaseOperationType operationType) { this.operationType = operationType; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    @Override
    public String toString() {
        return "ReleaseStatusReference{" +
                "jobId='" + jobId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", state=" + state +
                ", operationType=" + operationType +
                ", lastUpdatedAt=" + lastUpdatedAt +
                '}';
    }
}
