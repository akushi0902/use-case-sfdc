package com.opsera.integrator.sfdc.lifecycle.model;

import java.time.Instant;

/**
 * Persistence model for the {@code job_retention_metadata} table.
 *
 * <p>Controls data lifecycle under GDPR/CCPA retention policy.
 * Retention hint and classification mirror the governance values from
 * {@code RetentionCategoryHint} and {@code DataClassification}.
 */
public class JobRetentionMetadata {

    private String jobId;
    /** SHORT_TERM | STANDARD | EXTENDED | AUDIT_MANDATED */
    private String retentionHint;
    /** CONFIDENTIAL | RESTRICTED | INTERNAL | PUBLIC */
    private String dataClassification;
    private Instant purgeEligibleAt;
    private Instant purgedAt;
    private Instant createdAt;

    public JobRetentionMetadata() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getRetentionHint() { return retentionHint; }
    public void setRetentionHint(String retentionHint) { this.retentionHint = retentionHint; }

    public String getDataClassification() { return dataClassification; }
    public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }

    public Instant getPurgeEligibleAt() { return purgeEligibleAt; }
    public void setPurgeEligibleAt(Instant purgeEligibleAt) { this.purgeEligibleAt = purgeEligibleAt; }

    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant purgedAt) { this.purgedAt = purgedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "JobRetentionMetadata{jobId='" + jobId + "', retentionHint='" + retentionHint +
                "', dataClassification='" + dataClassification + "'}";
    }
}
