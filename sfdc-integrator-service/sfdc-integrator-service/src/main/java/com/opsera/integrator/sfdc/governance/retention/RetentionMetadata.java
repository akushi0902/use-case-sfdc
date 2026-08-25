package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;

import java.time.Instant;

/**
 * Full retention metadata record for a governed job or artifact reference.
 *
 * <p>Corresponds to the {@code job_retention_metadata} table (extended by V4 migration).
 * All timestamps are UTC. The {@code legalHold} flag overrides {@code purgeEligibilityStatus}
 * — when {@code legalHold} is true the status must always be {@link PurgeEligibilityStatus#LEGAL_HOLD}.
 */
public class RetentionMetadata {

    /** Job identifier (references {@code jobs.job_id}). */
    private String jobRef;
    /** Optional artifact or pipeline reference for artifact-scoped retention. */
    private String artifactRef;
    /** Governed data category driving the retention policy. */
    private GovernanceDataCategory dataCategory;
    /** Classification tier for the governed record. */
    private DataClassification classification;
    /** Retention category resolved from data category and classification. */
    private RetentionCategory retentionCategory;
    /** UTC timestamp when the retention window started (typically record creation). */
    private Instant retentionStart;
    /** UTC timestamp after which the record is eligible for purge (retentionStart + durationDays). */
    private Instant retentionExpiration;
    /** When true, automated purge is blocked regardless of retentionExpiration. */
    private boolean legalHold;
    /** Computed purge eligibility status. LEGAL_HOLD always takes precedence over ELIGIBLE. */
    private PurgeEligibilityStatus purgeEligibilityStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public RetentionMetadata() {}

    public String getJobRef() { return jobRef; }
    public void setJobRef(String jobRef) { this.jobRef = jobRef; }

    public String getArtifactRef() { return artifactRef; }
    public void setArtifactRef(String artifactRef) { this.artifactRef = artifactRef; }

    public GovernanceDataCategory getDataCategory() { return dataCategory; }
    public void setDataCategory(GovernanceDataCategory dataCategory) { this.dataCategory = dataCategory; }

    public DataClassification getClassification() { return classification; }
    public void setClassification(DataClassification classification) { this.classification = classification; }

    public RetentionCategory getRetentionCategory() { return retentionCategory; }
    public void setRetentionCategory(RetentionCategory retentionCategory) { this.retentionCategory = retentionCategory; }

    public Instant getRetentionStart() { return retentionStart; }
    public void setRetentionStart(Instant retentionStart) { this.retentionStart = retentionStart; }

    public Instant getRetentionExpiration() { return retentionExpiration; }
    public void setRetentionExpiration(Instant retentionExpiration) { this.retentionExpiration = retentionExpiration; }

    public boolean isLegalHold() { return legalHold; }
    public void setLegalHold(boolean legalHold) { this.legalHold = legalHold; }

    public PurgeEligibilityStatus getPurgeEligibilityStatus() { return purgeEligibilityStatus; }
    public void setPurgeEligibilityStatus(PurgeEligibilityStatus status) { this.purgeEligibilityStatus = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "RetentionMetadata{jobRef='" + jobRef + "', category=" + retentionCategory +
               ", classification=" + classification + ", legalHold=" + legalHold +
               ", purgeStatus=" + purgeEligibilityStatus + "}";
    }
}
