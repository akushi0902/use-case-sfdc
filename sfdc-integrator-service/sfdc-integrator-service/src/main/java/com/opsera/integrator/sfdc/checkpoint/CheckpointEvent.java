package com.opsera.integrator.sfdc.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deserialized Kafka checkpoint event payload.
 *
 * <p>Unknown fields are ignored ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) to
 * preserve compatibility with existing producers that may emit additional fields. Only
 * fields required for lifecycle persistence are bound here.
 *
 * <p>Sensitive fields (Salesforce org credentials, CLI output fragments, bearer tokens)
 * must never appear in this class. The mapper validates and discards any unsafe content
 * before persisting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckpointEvent {

    /** Durable job identifier referencing a {@code jobs} table row. */
    @JsonProperty("jobId")
    private String jobId;

    /** Correlation identifier for distributed tracing. Optional — generated if absent. */
    @JsonProperty("correlationId")
    private String correlationId;

    /** Machine-readable checkpoint code (e.g. "WORKER_STARTED", "EXECUTION_COMPLETED"). */
    @JsonProperty("checkpointCode")
    private String checkpointCode;

    /**
     * Broad status category used to determine lifecycle state transitions.
     * Expected values: RUNNING, COMPLETED, FAILED, CANCELLED, TIMED_OUT, PROGRESS.
     */
    @JsonProperty("statusCategory")
    private String statusCategory;

    /** Checkpoint sequence number for ordering within a job. */
    @JsonProperty("sequenceNumber")
    private int sequenceNumber;

    /** ISO-8601 timestamp when the checkpoint event was emitted. */
    @JsonProperty("eventTimestamp")
    private String eventTimestamp;

    /**
     * Safe human-readable summary. Must not contain credentials, CLI output, stack traces,
     * or Salesforce org details. Truncated to 512 characters by the mapper.
     */
    @JsonProperty("safeMessage")
    private String safeMessage;

    /** Producer-assigned message identifier used for duplicate detection. */
    @JsonProperty("messageId")
    private String messageId;

    /** Safe reason code for failure/cancellation events (e.g. "WORKER_TIMEOUT"). */
    @JsonProperty("safeReasonCode")
    private String safeReasonCode;

    public CheckpointEvent() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getCheckpointCode() { return checkpointCode; }
    public void setCheckpointCode(String checkpointCode) { this.checkpointCode = checkpointCode; }

    public String getStatusCategory() { return statusCategory; }
    public void setStatusCategory(String statusCategory) { this.statusCategory = statusCategory; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(String eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSafeReasonCode() { return safeReasonCode; }
    public void setSafeReasonCode(String safeReasonCode) { this.safeReasonCode = safeReasonCode; }

    /** Safe toString — never emits safeMessage content (may be untrusted). */
    @Override
    public String toString() {
        return "CheckpointEvent{jobId='" + jobId + "', checkpointCode='" + checkpointCode
                + "', statusCategory='" + statusCategory + "', seq=" + sequenceNumber + '}';
    }
}
