package com.opsera.integrator.sfdc.resources.v2.release;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V2 response DTO returned immediately after a release command is accepted for asynchronous
 * execution. Callers use {@code statusUrl} for subsequent polling.
 */
@Schema(description = "Acknowledgement returned when a v2 release command is accepted. "
        + "Use statusUrl to poll the job status.")
public class AcceptedAcknowledgement {

    @Schema(description = "Unique job identifier", example = "job-example-00000000-0000-0000-0000-000000000001")
    private String jobId;

    @Schema(description = "Correlation identifier for tracing this job across logs",
            example = "corr-example-00000000-0000-0000-0000-000000000001")
    private String correlationId;

    @Schema(description = "URL for polling this job's lifecycle state",
            example = "/api/v2/sfdc/release-jobs/job-example-00000000-0000-0000-0000-000000000001/status")
    private String statusUrl;

    @Schema(description = "Initial lifecycle state; always ACCEPTED on submission",
            example = "ACCEPTED")
    private ReleaseLifecycleState state;

    @Schema(description = "Timestamp when the command was accepted",
            example = "2024-01-01T00:00:00.000Z")
    private Instant acceptedAt;

    @Schema(description = "Operation type echoed from the request", example = "DEPLOY")
    private ReleaseOperationType operationType;

    @Schema(description = "Non-critical warnings from validation or routing. No sensitive values.")
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
