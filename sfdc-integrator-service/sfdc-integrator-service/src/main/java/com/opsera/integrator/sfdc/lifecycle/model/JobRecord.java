package com.opsera.integrator.sfdc.lifecycle.model;

import java.time.Instant;

/**
 * Persistence model for the {@code jobs} table.
 *
 * <p>Represents a single accepted release job. Raw Salesforce payloads, credentials,
 * bearer tokens, and Org URLs are never stored in this model. Customer references
 * are stored as hashed identifiers only.
 *
 * <p>The {@code version} field supports optimistic concurrency: callers must supply
 * the current version when updating state and handle the case where no row is updated
 * (concurrent modification).
 */
public class JobRecord {

    private String jobId;
    private String correlationId;
    private String operationType;
    private String currentState;
    /** SHA-256 hex of the customer identifier; never the raw value. */
    private String customerIdHash;
    private String sfdcToolId;
    private String pipelineId;
    private String stepId;
    private String dataClassification;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private long version;

    public JobRecord() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }

    public String getCustomerIdHash() { return customerIdHash; }
    public void setCustomerIdHash(String customerIdHash) { this.customerIdHash = customerIdHash; }

    public String getSfdcToolId() { return sfdcToolId; }
    public void setSfdcToolId(String sfdcToolId) { this.sfdcToolId = sfdcToolId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getDataClassification() { return dataClassification; }
    public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "JobRecord{jobId='" + jobId + "', correlationId='" + correlationId +
                "', operationType='" + operationType + "', currentState='" + currentState +
                "', version=" + version + '}';
    }
}
