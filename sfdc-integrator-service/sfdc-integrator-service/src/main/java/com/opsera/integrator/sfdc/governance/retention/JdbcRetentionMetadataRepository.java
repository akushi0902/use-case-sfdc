package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed implementation of {@link RetentionMetadataRepository}.
 *
 * <p>Writes to the {@code job_retention_metadata} table (extended by V4 migration).
 * Compatible with PostgreSQL (production) and H2 in PostgreSQL mode (tests).
 */
@Repository
public class JdbcRetentionMetadataRepository implements RetentionMetadataRepository {

    private final JdbcTemplate jdbc;

    public JdbcRetentionMetadataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RetentionMetadata metadata) {
        try {
            jdbc.update(
                "INSERT INTO job_retention_metadata " +
                "(job_id, artifact_ref, data_category, retention_hint, data_classification, " +
                " retention_start, purge_eligible_at, legal_hold, purge_eligibility_status, " +
                " created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                metadata.getJobRef(),
                metadata.getArtifactRef(),
                metadata.getDataCategory() != null ? metadata.getDataCategory().name() : null,
                metadata.getRetentionCategory() != null ? metadata.getRetentionCategory().name() : RetentionCategory.STANDARD.name(),
                metadata.getClassification() != null ? metadata.getClassification().name() : DataClassification.CONFIDENTIAL.name(),
                toTimestamp(metadata.getRetentionStart()),
                toTimestamp(metadata.getRetentionExpiration()),
                metadata.isLegalHold(),
                metadata.getPurgeEligibilityStatus() != null ? metadata.getPurgeEligibilityStatus().name() : PurgeEligibilityStatus.INELIGIBLE.name(),
                toTimestamp(metadata.getCreatedAt()),
                toTimestamp(metadata.getUpdatedAt())
            );
        } catch (Exception ex) {
            throw new RetentionAssignmentException(
                "Failed to persist retention metadata: jobRef=" + metadata.getJobRef(),
                metadata.getJobRef(),
                "PERSISTENCE_FAILURE",
                ex);
        }
    }

    @Override
    public Optional<RetentionMetadata> findByJobRef(String jobRef) {
        List<RetentionMetadata> results = jdbc.query(
            "SELECT * FROM job_retention_metadata WHERE job_id = ?",
            RETENTION_ROW_MAPPER, jobRef);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<RetentionMetadata> findPurgeCandidates(Instant now, int maxResults) {
        return jdbc.query(
            "SELECT * FROM job_retention_metadata " +
            "WHERE purge_eligible_at <= ? " +
            "  AND purge_eligibility_status = 'ELIGIBLE' " +
            "  AND legal_hold = FALSE " +
            "  AND purged_at IS NULL " +
            "ORDER BY purge_eligible_at ASC " +
            "LIMIT ?",
            RETENTION_ROW_MAPPER, toTimestamp(now), maxResults);
    }

    @Override
    public int markPurged(String jobRef, Instant purgedAt) {
        return jdbc.update(
            "UPDATE job_retention_metadata SET purged_at = ?, updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = ?",
            toTimestamp(purgedAt), jobRef);
    }

    @Override
    public void updateLegalHold(String jobRef, boolean legalHold, PurgeEligibilityStatus newStatus) {
        int updated = jdbc.update(
            "UPDATE job_retention_metadata SET legal_hold = ?, purge_eligibility_status = ?, " +
            "updated_at = CURRENT_TIMESTAMP WHERE job_id = ?",
            legalHold,
            newStatus != null ? newStatus.name() : PurgeEligibilityStatus.LEGAL_HOLD.name(),
            jobRef);
        if (updated == 0) {
            throw new RetentionAssignmentException(
                "Legal hold update failed — record not found: jobRef=" + jobRef,
                jobRef,
                "RECORD_NOT_FOUND");
        }
    }

    private static final RowMapper<RetentionMetadata> RETENTION_ROW_MAPPER = (rs, rowNum) -> {
        RetentionMetadata m = new RetentionMetadata();
        m.setJobRef(rs.getString("job_id"));
        m.setArtifactRef(rs.getString("artifact_ref"));
        String dataCat = rs.getString("data_category");
        if (dataCat != null) {
            try { m.setDataCategory(GovernanceDataCategory.valueOf(dataCat)); }
            catch (IllegalArgumentException ignored) {}
        }
        String hint = rs.getString("retention_hint");
        if (hint != null) {
            try { m.setRetentionCategory(RetentionCategory.valueOf(hint)); }
            catch (IllegalArgumentException ignored) {}
        }
        String classif = rs.getString("data_classification");
        if (classif != null) {
            try { m.setClassification(DataClassification.valueOf(classif)); }
            catch (IllegalArgumentException ignored) {}
        }
        m.setRetentionStart(toInstant(rs.getTimestamp("retention_start")));
        m.setRetentionExpiration(toInstant(rs.getTimestamp("purge_eligible_at")));
        m.setLegalHold(rs.getBoolean("legal_hold"));
        String status = rs.getString("purge_eligibility_status");
        if (status != null) {
            try { m.setPurgeEligibilityStatus(PurgeEligibilityStatus.valueOf(status)); }
            catch (IllegalArgumentException ignored) {}
        }
        m.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        m.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        m.setPurgedAt(toInstant(rs.getTimestamp("purged_at")));
        return m;
    };

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
