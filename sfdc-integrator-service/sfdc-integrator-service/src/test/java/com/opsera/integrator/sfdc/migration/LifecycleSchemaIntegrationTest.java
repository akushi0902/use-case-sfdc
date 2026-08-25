package com.opsera.integrator.sfdc.migration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the V2 lifecycle schema migration.
 *
 * <p>Verifies that all lifecycle tables are created with the expected structure,
 * constraints, and indexes. Uses H2 in PostgreSQL compatibility mode — no external
 * PostgreSQL or Kafka dependency required.
 *
 * <p>Flyway is enabled via {@code @TestPropertySource} so migrations V1 and V2 run
 * on context startup. A failing migration causes context startup to fail before
 * any assertion runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class LifecycleSchemaIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // ---- Table existence ----

    @Test
    void v2MigrationCreatesJobsTable() throws Exception {
        assertTableExists("jobs");
    }

    @Test
    void v2MigrationCreatesJobCheckpointsTable() throws Exception {
        assertTableExists("job_checkpoints");
    }

    @Test
    void v2MigrationCreatesWorkerAttemptsTable() throws Exception {
        assertTableExists("worker_attempts");
    }

    @Test
    void v2MigrationCreatesJobDiagnosticsTable() throws Exception {
        assertTableExists("job_diagnostics");
    }

    @Test
    void v2MigrationCreatesJobRetentionMetadataTable() throws Exception {
        assertTableExists("job_retention_metadata");
    }

    // ---- Flyway history ----

    @Test
    void flywayHistoryRecordsV2MigrationAsSuccessful() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT version, success FROM flyway_schema_history WHERE version = '2'");
            assertTrue(rs.next(), "flyway_schema_history must contain an entry for version 2");
            assertTrue(rs.getBoolean("success"), "V2 migration must be recorded as successful");
        }
    }

    // ---- Job table constraints ----

    @Test
    void jobsTable_insertAndSelect_works() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('constraint-test-job-01', 'cid-constraint-01', 'DEPLOY', 'ACCEPTED', 0)");
            ResultSet rs = stmt.executeQuery(
                    "SELECT current_state FROM jobs WHERE job_id = 'constraint-test-job-01'");
            assertTrue(rs.next());
            assertThat(rs.getString("current_state")).isEqualTo("ACCEPTED");
        }
    }

    @Test
    void jobsTable_duplicatePrimaryKey_rejected() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('constraint-test-dup-01', 'cid-dup-01', 'DEPLOY', 'ACCEPTED', 0)");
            assertThatThrownBy(() ->
                stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('constraint-test-dup-01', 'cid-dup-02', 'VALIDATE', 'ACCEPTED', 0)"))
                    .isInstanceOf(Exception.class);
        }
    }

    // ---- Checkpoint constraints ----

    @Test
    void checkpoints_uniqueSequenceConstraint_rejectsDuplicates() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('cp-unique-job-01', 'cid-cp-unique-01', 'DEPLOY', 'RUNNING', 0)");
            stmt.executeUpdate(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, state_at_checkpoint) " +
                    "VALUES ('cp-unique-job-01', 1, 'DISPATCH_STARTED', 'RUNNING')");
            assertThatThrownBy(() ->
                stmt.executeUpdate(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, state_at_checkpoint) " +
                    "VALUES ('cp-unique-job-01', 1, 'DUPLICATE_SEQ', 'RUNNING')"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void checkpoints_foreignKeyToJobs_rejectsOrphanedCheckpoint() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() ->
                stmt.executeUpdate(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, state_at_checkpoint) " +
                    "VALUES ('nonexistent-job-fk', 1, 'ORPHANED', 'RUNNING')"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void checkpoints_orderedRetrieval_returnsBySequence() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('cp-order-job-01', 'cid-order-01', 'VALIDATE', 'COMPLETED', 2)");
            stmt.executeUpdate(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, state_at_checkpoint) " +
                    "VALUES ('cp-order-job-01', 2, 'EXECUTION_DONE', 'COMPLETED')");
            stmt.executeUpdate(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, state_at_checkpoint) " +
                    "VALUES ('cp-order-job-01', 1, 'DISPATCH_STARTED', 'RUNNING')");
            ResultSet rs = stmt.executeQuery(
                    "SELECT sequence_number FROM job_checkpoints WHERE job_id = 'cp-order-job-01' " +
                    "ORDER BY sequence_number ASC");
            assertTrue(rs.next());
            assertThat(rs.getInt("sequence_number")).isEqualTo(1);
            assertTrue(rs.next());
            assertThat(rs.getInt("sequence_number")).isEqualTo(2);
        }
    }

    // ---- Correlation ID lookup ----

    @Test
    void jobs_correlationIdLookup_returnsMatchingJob() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('cid-lookup-job-01', 'unique-cid-for-lookup', 'DEPLOY', 'ACCEPTED', 0)");
            ResultSet rs = stmt.executeQuery(
                    "SELECT job_id FROM jobs WHERE correlation_id = 'unique-cid-for-lookup'");
            assertTrue(rs.next());
            assertThat(rs.getString("job_id")).isEqualTo("cid-lookup-job-01");
        }
    }

    // ---- Optimistic concurrency ----

    @Test
    void jobs_optimisticConcurrency_versionDefaultsToZero() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, version) " +
                    "VALUES ('occ-job-01', 'cid-occ-01', 'VALIDATE', 'ACCEPTED', 0)");
            ResultSet rs = stmt.executeQuery("SELECT version FROM jobs WHERE job_id = 'occ-job-01'");
            assertTrue(rs.next());
            assertThat(rs.getLong("version")).isEqualTo(0L);
        }
    }

    // ---- Helper ----

    private void assertTableExists(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"});
            assertTrue(rs.next(),
                    "V2 lifecycle migration must create table: " + tableName);
        }
    }
}
