package com.opsera.integrator.sfdc.lifecycle.model;

import java.time.Instant;

/**
 * Persistence model for the {@code job_checkpoints} table.
 *
 * <p>Checkpoints are immutable progress records appended in sequence order.
 * Duplicate sequence numbers for the same job are rejected by the unique constraint.
 * {@code safeMessage} must never contain raw payloads, credentials, or stack traces.
 */
public class JobCheckpoint {

    private long id;
    private String jobId;
    private int sequenceNumber;
    private String checkpointCode;
    private String stateAtCheckpoint;
    private String safeMessage;
    private Instant createdAt;

    public JobCheckpoint() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getCheckpointCode() { return checkpointCode; }
    public void setCheckpointCode(String checkpointCode) { this.checkpointCode = checkpointCode; }

    public String getStateAtCheckpoint() { return stateAtCheckpoint; }
    public void setStateAtCheckpoint(String stateAtCheckpoint) { this.stateAtCheckpoint = stateAtCheckpoint; }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "JobCheckpoint{jobId='" + jobId + "', seq=" + sequenceNumber +
                ", code='" + checkpointCode + "', state='" + stateAtCheckpoint + "'}";
    }
}
