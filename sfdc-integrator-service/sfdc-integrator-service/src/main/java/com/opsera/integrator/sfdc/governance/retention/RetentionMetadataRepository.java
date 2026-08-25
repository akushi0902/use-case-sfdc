package com.opsera.integrator.sfdc.governance.retention;

import java.util.Optional;

/**
 * Repository interface for retention metadata persistence.
 *
 * <p>Implementations write to the {@code job_retention_metadata} table.
 * All operations use job reference identifiers — no raw customer PII.
 */
public interface RetentionMetadataRepository {

    /**
     * Persists a new retention metadata record.
     * Throws {@link RetentionAssignmentException} if the insert fails.
     */
    void save(RetentionMetadata metadata);

    /**
     * Retrieves the retention metadata for the given job reference.
     */
    Optional<RetentionMetadata> findByJobRef(String jobRef);

    /**
     * Updates the legal hold flag and recomputes purge eligibility status
     * for the given job reference.
     *
     * @param jobRef    opaque job reference
     * @param legalHold new legal hold state
     * @param newStatus recomputed purge eligibility status (must be LEGAL_HOLD when legalHold=true)
     * @throws RetentionAssignmentException if the record does not exist or the update fails
     */
    void updateLegalHold(String jobRef, boolean legalHold, PurgeEligibilityStatus newStatus);
}
