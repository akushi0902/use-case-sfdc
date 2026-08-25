package com.opsera.integrator.sfdc.resources.v2.release;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rollback decision support fields included in the v2 release job status response.
 *
 * <p>Fields are advisory only and never trigger rollback execution.
 * No raw logs, stack traces, Salesforce payloads, credentials, or tokens are included.
 * The {@code diagnosticSummary} is redacted and truncated before population.
 *
 * <p>Use {@link Builder} to construct instances.
 */
@Schema(description = "Advisory rollback decision support fields derived from lifecycle state and checkpoints")
public class RollbackDecision {

    @Schema(description = "Whether this job is eligible for rollback based on operation type and state")
    private Boolean rollbackEligible;

    @Schema(description = "Whether rollback is recommended based on the failure mode")
    private Boolean rollbackRecommended;

    @Schema(description = "Reason code explaining the rollback recommendation or ineligibility")
    private RollbackReasonCode rollbackReasonCode;

    @Schema(description = "Stage at which the job failed or was interrupted, if determinable")
    private FailedStage failedStage;

    @Schema(description = "Checkpoint code of the last successfully completed step before failure")
    private String lastSuccessfulCheckpoint;

    @Schema(description = "Safe, redacted diagnostic summary. No stack traces, credentials, or raw payloads.")
    private String diagnosticSummary;

    @Schema(description = "Safe artifact reference identifiers available for rollback, if any. Never raw paths.")
    private List<String> artifactReferences;

    @Schema(description = "Timestamp when rollback decision fields were last evaluated")
    private Instant updatedAt;

    @Schema(description = "Confidence note for operator guidance; empty when decision is fully determined")
    private String confidenceNote;

    private RollbackDecision() {
        this.artifactReferences = new ArrayList<>();
    }

    public Boolean getRollbackEligible() { return rollbackEligible; }
    public Boolean getRollbackRecommended() { return rollbackRecommended; }
    public RollbackReasonCode getRollbackReasonCode() { return rollbackReasonCode; }
    public FailedStage getFailedStage() { return failedStage; }
    public String getLastSuccessfulCheckpoint() { return lastSuccessfulCheckpoint; }
    public String getDiagnosticSummary() { return diagnosticSummary; }

    public List<String> getArtifactReferences() {
        return Collections.unmodifiableList(artifactReferences);
    }

    public Instant getUpdatedAt() { return updatedAt; }
    public String getConfidenceNote() { return confidenceNote; }

    @Override
    public String toString() {
        return "RollbackDecision{rollbackEligible=" + rollbackEligible +
                ", rollbackRecommended=" + rollbackRecommended +
                ", rollbackReasonCode=" + rollbackReasonCode +
                ", failedStage=" + failedStage +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final RollbackDecision instance = new RollbackDecision();

        public Builder rollbackEligible(Boolean v) { instance.rollbackEligible = v; return this; }
        public Builder rollbackRecommended(Boolean v) { instance.rollbackRecommended = v; return this; }
        public Builder rollbackReasonCode(RollbackReasonCode v) { instance.rollbackReasonCode = v; return this; }
        public Builder failedStage(FailedStage v) { instance.failedStage = v; return this; }
        public Builder lastSuccessfulCheckpoint(String v) { instance.lastSuccessfulCheckpoint = v; return this; }
        public Builder diagnosticSummary(String v) { instance.diagnosticSummary = v; return this; }
        public Builder artifactReferences(List<String> v) {
            instance.artifactReferences = v != null ? new ArrayList<>(v) : new ArrayList<>();
            return this;
        }
        public Builder updatedAt(Instant v) { instance.updatedAt = v; return this; }
        public Builder confidenceNote(String v) { instance.confidenceNote = v; return this; }

        public RollbackDecision build() { return instance; }
    }
}
