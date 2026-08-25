package com.opsera.integrator.sfdc.governance.retention;

import java.time.Instant;
import java.util.List;
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

    /**
     * Selects up to {@code maxResults} records eligible for purge:
     * retention expiration has passed, status is ELIGIBLE, legal hold is false,
     * and the record has not yet been purged ({@code purged_at} is NULL).
     *
     * <p>Results are ordered by {@code purge_eligible_at} ascending (oldest-first).
     *
     * @param now        current UTC timestamp; records with purge_eligible_at <= now are included
     * @param maxResults maximum number of candidates to return (batch size limit)
     * @return list of purge candidate records, may be empty
     */
    List<RetentionMetadata> findPurgeCandidates(Instant now, int maxResults);

    /**
     * Marks a retention metadata record as purged by setting {@code purged_at}.
     * Does not affect the {@code purge_eligibility_status} column — callers
     * should emit an audit event to record the completed purge action.
     *
     * @param jobRef   opaque job reference
     * @param purgedAt UTC timestamp of the purge action
     * @return number of rows updated (0 if job not found, 1 on success)
     */
    int markPurged(String jobRef, Instant purgedAt);
}
