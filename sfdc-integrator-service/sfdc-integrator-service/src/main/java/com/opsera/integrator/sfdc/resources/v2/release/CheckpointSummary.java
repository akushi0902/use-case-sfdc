package com.opsera.integrator.sfdc.resources.v2.release;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Safe summary of a single job checkpoint from the ordered checkpoint history.
 *
 * <p>Only pre-sanitized fields are included. Raw Kafka payloads, Salesforce CLI output,
 * stack traces, credentials, and unrestricted artifact paths are never present.
 */
@Schema(description = "Safe summary of a single ordered checkpoint in the job lifecycle history")
public class CheckpointSummary {

    @Schema(description = "Monotonically increasing sequence number within this job", example = "1")
    private int sequenceNumber;

    @Schema(description = "Checkpoint code identifying the event type", example = "WORKER_STARTED")
    private String checkpointCode;

    @Schema(description = "Lifecycle state at the time this checkpoint was recorded", example = "RUNNING")
    private String stateAtCheckpoint;

    @Schema(description = "Safe operational message. No raw payloads, credentials, or stack traces.",
            example = "Worker pod started execution")
    private String safeMessage;

    @Schema(description = "Timestamp when this checkpoint was recorded")
    private Instant recordedAt;

    public CheckpointSummary() {}

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getCheckpointCode() { return checkpointCode; }
    public void setCheckpointCode(String checkpointCode) { this.checkpointCode = checkpointCode; }

    public String getStateAtCheckpoint() { return stateAtCheckpoint; }
    public void setStateAtCheckpoint(String stateAtCheckpoint) { this.stateAtCheckpoint = stateAtCheckpoint; }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    @Override
    public String toString() {
        return "CheckpointSummary{seq=" + sequenceNumber +
                ", code='" + checkpointCode + '\'' +
                ", state='" + stateAtCheckpoint + '\'' +
                ", recordedAt=" + recordedAt + '}';
    }
}
