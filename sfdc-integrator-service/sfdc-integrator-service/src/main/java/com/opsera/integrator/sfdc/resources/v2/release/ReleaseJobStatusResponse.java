package com.opsera.integrator.sfdc.resources.v2.release;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V2 job status response returned by GET /api/v2/sfdc/release-jobs/{jobId}/status.
 *
 * <p>Presents release job lifecycle information from the best available status source.
 * Raw Kafka payloads, shell output, stack traces, credentials, full Salesforce payloads,
 * and unrestricted artifact paths are never included.
 *
 * <p>The {@code statusSource} field identifies which adapter provided the data.
 * This allows callers and operators to understand the fidelity of the status information
 * and aligns with AC-6: the current adapter source is clearly labeled so a future
 * durable lifecycle service can replace it without changing the public contract.
 */
@Schema(description = "V2 release job lifecycle status response")
public class ReleaseJobStatusResponse {

    @Schema(description = "Unique job identifier", example = "job-status-fixture-001")
    private String jobId;

    @Schema(description = "Correlation identifier for tracing this job across logs",
            example = "corr-status-fixture-001")
    private String correlationId;

    @Schema(description = "Operation type requested", example = "QUICK_DEPLOY")
    private String operationType;

    @Schema(description = "Current lifecycle state of the job")
    private ReleaseLifecycleState state;

    @Schema(description = "Timestamp when the job was first accepted for execution")
    private Instant acceptedAt;

    @Schema(description = "Timestamp of the most recent lifecycle state change")
    private Instant updatedAt;

    @Schema(description = "Ordered checkpoint history, sorted by sequence number ascending. "
            + "Empty when the job has no checkpoints yet.")
    private List<CheckpointSummary> checkpoints;

    @Schema(description = "Safe diagnostic summary for terminal failed or cancelled states. "
            + "No stack traces, credentials, raw payloads, or artifact paths.")
    private String safeDiagnosticSummary;

    @Schema(description = "Reference to the Salesforce deployment result or artifact, if available. "
            + "Never contains raw CLI output or unrestricted paths.")
    private String resultReference;

    @Schema(description = "Non-critical operational warnings. No sensitive values.")
    private List<String> safeWarnings;

    /**
     * Identifies which adapter provided this status response.
     * Currently: DURABLE_LIFECYCLE (backed by the durable job repository).
     * Replace this adapter with a future PostgreSQL-backed lifecycle service
     * by implementing {@link com.opsera.integrator.sfdc.services.v2.JobStatusAdapter}.
     */
    @Schema(description = "Identifies which adapter provided this status response",
            example = "DURABLE_LIFECYCLE")
    private String statusSource;

    @Schema(description = "Advisory rollback decision support fields. Present for all P0 release jobs.")
    private RollbackDecision rollbackDecision;

    public ReleaseJobStatusResponse() {
        this.checkpoints = new ArrayList<>();
        this.safeWarnings = new ArrayList<>();
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public ReleaseLifecycleState getState() { return state; }
    public void setState(ReleaseLifecycleState state) { this.state = state; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<CheckpointSummary> getCheckpoints() {
        return Collections.unmodifiableList(checkpoints);
    }
    public void setCheckpoints(List<CheckpointSummary> checkpoints) {
        this.checkpoints = checkpoints != null ? new ArrayList<>(checkpoints) : new ArrayList<>();
    }

    public String getSafeDiagnosticSummary() { return safeDiagnosticSummary; }
    public void setSafeDiagnosticSummary(String safeDiagnosticSummary) {
        this.safeDiagnosticSummary = safeDiagnosticSummary;
    }

    public String getResultReference() { return resultReference; }
    public void setResultReference(String resultReference) { this.resultReference = resultReference; }

    public List<String> getSafeWarnings() { return Collections.unmodifiableList(safeWarnings); }
    public void setSafeWarnings(List<String> safeWarnings) {
        this.safeWarnings = safeWarnings != null ? new ArrayList<>(safeWarnings) : new ArrayList<>();
    }

    public String getStatusSource() { return statusSource; }
    public void setStatusSource(String statusSource) { this.statusSource = statusSource; }

    public RollbackDecision getRollbackDecision() { return rollbackDecision; }
    public void setRollbackDecision(RollbackDecision rollbackDecision) {
        this.rollbackDecision = rollbackDecision;
    }

    @Override
    public String toString() {
        return "ReleaseJobStatusResponse{" +
                "jobId='" + jobId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", state=" + state +
                ", operationType='" + operationType + '\'' +
                ", statusSource='" + statusSource + '\'' +
                ", checkpointCount=" + checkpoints.size() +
                '}';
    }
}
