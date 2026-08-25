package com.opsera.integrator.sfdc.resources.v2.cancel;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Response DTO returned when a v2 cancellation request is accepted or confirmed.
 *
 * <p>Callers should use {@code statusUrl} to poll the final terminal state.
 */
@Schema(description = "Acknowledgement returned when a v2 cancellation request is accepted.")
public class CancellationAcknowledgement {

    @Schema(description = "Job identifier that was cancelled", example = "job-example-001")
    private String jobId;

    @Schema(description = "Correlation identifier for tracing this cancellation across logs",
            example = "corr-example-001")
    private String correlationId;

    @Schema(description = "URL for polling the final job lifecycle state",
            example = "/api/v2/sfdc/release-jobs/job-example-001/status")
    private String statusUrl;

    @Schema(description = "Lifecycle state after cancellation; CANCELLED on success",
            example = "CANCELLED")
    private String state;

    @Schema(description = "Timestamp when cancellation was requested",
            example = "2026-08-25T10:00:00.000Z")
    private Instant cancellationRequestedAt;

    @Schema(description = "Safe operator-readable message. Never contains credentials or stack traces.",
            example = "Cancellation accepted")
    private String safeMessage;

    public CancellationAcknowledgement() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }
    public void setCancellationRequestedAt(Instant cancellationRequestedAt) {
        this.cancellationRequestedAt = cancellationRequestedAt;
    }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }
}
