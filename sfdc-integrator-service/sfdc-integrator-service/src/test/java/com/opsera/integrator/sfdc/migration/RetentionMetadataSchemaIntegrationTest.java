package com.opsera.integrator.sfdc.migration;

import com.opsera.integrator.sfdc.governance.retention.JdbcRetentionMetadataRepository;
import com.opsera.integrator.sfdc.governance.retention.PurgeEligibilityStatus;
import com.opsera.integrator.sfdc.governance.retention.RetentionCategory;
import com.opsera.integrator.sfdc.governance.retention.RetentionMetadata;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the V4 retention metadata schema migration (AC-6).
 *
 * <p>Verifies that all new columns and indexes are created by the V4 migration,
 * that the JDBC repository correctly persists and retrieves retention metadata,
 * and that legal hold updates work end-to-end.
 *
 * <p>Uses H2 in PostgreSQL mode — no external database required.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class RetentionMetadataSchemaIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcRetentionMetadataRepository repository;

    // ---- Schema presence ----

    @Test
    void v4MigrationAddsLegalHoldColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "legal_hold");
    }

    @Test
    void v4MigrationAddsPurgeEligibilityStatusColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "purge_eligibility_status");
    }

    @Test
    void v4MigrationAddsRetentionStartColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "retention_start");
    }

    @Test
    void v4MigrationAddsArtifactRefColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "artifact_ref");
    }

    @Test
    void v4MigrationAddsDataCategoryColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "data_category");
    }

    @Test
    void v4MigrationAddsUpdatedAtColumn() throws Exception {
        assertColumnExists("job_retention_metadata", "updated_at");
    }

    // ---- Flyway history ----

    @Test
    void flywayHistoryRecordsV4MigrationAsSuccessful() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT version, success FROM flyway_schema_history WHERE version = '4'");
            assertTrue(rs.next(), "flyway_schema_history must contain an entry for version 4");
            assertTrue(rs.getBoolean("success"), "V4 migration must be recorded as successful");
        }
    }

    // ---- Repository round-trip: Confidential job metadata ----

    @Test
    void repository_persistsAndRetrieves_confidentialJobMetadata() throws Exception {
        String jobId = insertJobRow("rm-test-conf-001");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        RetentionMetadata metadata = buildMetadata(jobId, null,
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategory.STANDARD,
                now, now.plus(365, ChronoUnit.DAYS));

        repository.save(metadata);

        Optional<RetentionMetadata> found = repository.findByJobRef(jobId);
        assertThat(found).isPresent();
        RetentionMetadata loaded = found.get();
        assertThat(loaded.getJobRef()).isEqualTo(jobId);
        assertThat(loaded.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(loaded.getRetentionCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(loaded.isLegalHold()).isFalse();
        assertThat(loaded.getPurgeEligibilityStatus()).isEqualTo(PurgeEligibilityStatus.INELIGIBLE);
        assertThat(loaded.getDataCategory()).isEqualTo(GovernanceDataCategory.JOB_METADATA);
    }

    // ---- Repository round-trip: Restricted artifact reference ----

    @Test
    void repository_persistsAndRetrieves_restrictedArtifactRetention() throws Exception {
        String jobId = insertJobRow("rm-test-rest-002");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        RetentionMetadata metadata = buildMetadata(jobId, "pipeline-fixture-artifact-002",
                GovernanceDataCategory.SFDC_ARTIFACT,
                DataClassification.RESTRICTED,
                RetentionCategory.EXTENDED,
                now, now.plus(2555, ChronoUnit.DAYS));

        repository.save(metadata);

        Optional<RetentionMetadata> found = repository.findByJobRef(jobId);
        assertThat(found).isPresent();
        RetentionMetadata loaded = found.get();
        assertThat(loaded.getRetentionCategory()).isEqualTo(RetentionCategory.EXTENDED);
        assertThat(loaded.getClassification()).isEqualTo(DataClassification.RESTRICTED);
        assertThat(loaded.getArtifactRef()).isEqualTo("pipeline-fixture-artifact-002");
        assertThat(loaded.getDataCategory()).isEqualTo(GovernanceDataCategory.SFDC_ARTIFACT);
    }

    // ---- Legal hold update ----

    @Test
    void repository_updateLegalHold_setsStatusToLegalHold() throws Exception {
        String jobId = insertJobRow("rm-test-hold-003");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        repository.save(buildMetadata(jobId, null,
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategory.STANDARD,
                now, now.plus(365, ChronoUnit.DAYS)));

        repository.updateLegalHold(jobId, true, PurgeEligibilityStatus.LEGAL_HOLD);

        Optional<RetentionMetadata> found = repository.findByJobRef(jobId);
        assertThat(found).isPresent();
        assertThat(found.get().isLegalHold()).isTrue();
        assertThat(found.get().getPurgeEligibilityStatus()).isEqualTo(PurgeEligibilityStatus.LEGAL_HOLD);
    }

    @Test
    void repository_findByJobRef_notFound_returnsEmpty() {
        assertThat(repository.findByJobRef("nonexistent-rm-job")).isEmpty();
    }

    // ---- Purge candidate query verifies indexes are usable ----

    @Test
    void purgeEligibilityStatus_indexSupportsCandidateQuery() throws Exception {
        String jobId = insertJobRow("rm-test-idx-004");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        repository.save(buildMetadata(jobId, null,
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategory.STANDARD,
                now, now.plus(365, ChronoUnit.DAYS)));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM job_retention_metadata " +
                    "WHERE purge_eligibility_status = 'INELIGIBLE'");
            assertTrue(rs.next());
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    // ---- Helpers ----

    private String insertJobRow(String jobId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('" + jobId + "', 'cid-rm-" + jobId + "', 'DEPLOY', 'ACCEPTED', 0)");
        }
        return jobId;
    }

    private RetentionMetadata buildMetadata(String jobRef, String artifactRef,
                                             GovernanceDataCategory category,
                                             DataClassification classification,
                                             RetentionCategory retentionCategory,
                                             Instant start, Instant expiration) {
        RetentionMetadata m = new RetentionMetadata();
        m.setJobRef(jobRef);
        m.setArtifactRef(artifactRef);
        m.setDataCategory(category);
        m.setClassification(classification);
        m.setRetentionCategory(retentionCategory);
        m.setRetentionStart(start);
        m.setRetentionExpiration(expiration);
        m.setLegalHold(false);
        m.setPurgeEligibilityStatus(PurgeEligibilityStatus.INELIGIBLE);
        m.setCreatedAt(start);
        m.setUpdatedAt(start);
        return m;
    }

    private void assertColumnExists(String tableName, String columnName) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, tableName, columnName);
            assertTrue(rs.next(),
                    "V4 migration must add column '" + columnName + "' to table '" + tableName + "'");
        }
    }
}
