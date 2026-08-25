package com.opsera.integrator.sfdc.resources.v2.release;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V2 response DTO returned immediately after a release command is accepted for asynchronous
 * execution. Callers use {@code statusUrl} for subsequent polling.
 */
public class AcceptedAcknowledgement {

    private String jobId;
    private String correlationId;
    private String statusUrl;
    private ReleaseLifecycleState state;
    private Instant acceptedAt;
    private ReleaseOperationType operationType;
    private List<String> safeWarnings;

    public AcceptedAcknowledgement() {
        this.safeWarnings = new ArrayList<>();
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public ReleaseLifecycleState getState() { return state; }
    public void setState(ReleaseLifecycleState state) { this.state = state; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public ReleaseOperationType getOperationType() { return operationType; }
    public void setOperationType(ReleaseOperationType operationType) { this.operationType = operationType; }

    public List<String> getSafeWarnings() { return Collections.unmodifiableList(safeWarnings); }
    public void setSafeWarnings(List<String> safeWarnings) {
        this.safeWarnings = safeWarnings != null ? new ArrayList<>(safeWarnings) : new ArrayList<>();
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            this.safeWarnings.add(warning);
        }
    }

    @Override
    public String toString() {
        return "AcceptedAcknowledgement{" +
                "jobId='" + jobId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", state=" + state +
                ", acceptedAt=" + acceptedAt +
                ", operationType=" + operationType +
                ", safeWarningsCount=" + (safeWarnings != null ? safeWarnings.size() : 0) +
                '}';
    }
}
