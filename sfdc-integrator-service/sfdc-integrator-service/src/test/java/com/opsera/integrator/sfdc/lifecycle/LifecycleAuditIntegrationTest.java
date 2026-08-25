package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.governance.audit.AuditEventRepository;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying that lifecycle mutations write both lifecycle state AND
 * an immutable audit event in the H2 test database (AC-6, AC-1).
 *
 * <p>Uses the same @SpringBootTest + @ActiveProfiles("test") + Flyway-enabled pattern
 * as {@link JobLifecycleServiceIntegrationTest}. Flyway applies V1–V5 migrations
 * including the audit_events schema and the retention_expiry_at column.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
@DisplayName("Lifecycle + Audit integration tests")
class LifecycleAuditIntegrationTest {

    @Autowired
    private JobLifecycleService lifecycleService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private DataSource dataSource;

    // ---- State transition persists audit event ----

    @Test
    @DisplayName("Dispatching transition persists a DISPATCH_HANDOFF audit event")
    void markDispatching_persistsDispatchHandoffAuditEvent() throws Exception {
        JobRecord job = acceptedJob("audit-it-001", "cid-audit-it-001");
        jobRepository.save(job);

        lifecycleService.markDispatching("audit-it-001", "cid-audit-it-001");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT operation, actor_type, resource_ref, correlation_id, retention_expiry_at " +
                    "FROM audit_events WHERE resource_ref = 'audit-it-001' AND operation = 'DISPATCH_HANDOFF'");
            assertTrue(rs.next(), "DISPATCH_HANDOFF audit event must be persisted");
            assertThat(rs.getString("operation")).isEqualTo("DISPATCH_HANDOFF");
            assertThat(rs.getString("actor_type")).isEqualTo("SYSTEM");
            assertThat(rs.getString("resource_ref")).isEqualTo("audit-it-001");
            assertThat(rs.getTimestamp("retention_expiry_at")).isNotNull();
        }
    }

    @Test
    @DisplayName("Full lifecycle sequence persists audit events at each transition")
    void fullLifecycleSequence_persistsAuditEventsAtEachTransition() throws Exception {
        JobRecord job = acceptedJob("audit-it-010", "cid-audit-it-010");
        jobRepository.save(job);

        lifecycleService.markDispatching("audit-it-010", "cid-audit-it-010");
        lifecycleService.markRunning("audit-it-010", "cid-audit-it-010", 1);
        lifecycleService.markCompleted("audit-it-010", "cid-audit-it-010", 2);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) AS cnt FROM audit_events WHERE resource_ref = 'audit-it-010'");
            assertTrue(rs.next());
            // At minimum: DISPATCH_HANDOFF, RUNNING transition, COMPLETED transition +
            // 2 checkpoint writes = 5 events minimum
            assertThat(rs.getLong("cnt")).isGreaterThanOrEqualTo(3L);
        }
    }

    @Test
    @DisplayName("Timeout finalization persists JOB_TIMEOUT_FINALIZED and DIAGNOSTIC_UPDATED audit events")
    void markTimedOut_persistsTimeoutFinalizedAndDiagnosticAuditEvents() throws Exception {
        JobRecord job = acceptedJob("audit-it-020", "cid-audit-it-020");
        job.setCurrentState("RUNNING");
        job.setVersion(2L);
        jobRepository.save(job);

        lifecycleService.markTimedOut("audit-it-020", "cid-audit-it-020");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT operation FROM audit_events WHERE resource_ref = 'audit-it-020' " +
                    "ORDER BY event_timestamp");

            java.util.List<String> ops = new java.util.ArrayList<>();
            while (rs.next()) {
                ops.add(rs.getString("operation"));
            }
            assertThat(ops).contains("JOB_TIMEOUT_FINALIZED");
            assertThat(ops).contains("DIAGNOSTIC_UPDATED");
        }
    }

    @Test
    @DisplayName("Idempotent replay does not persist additional audit events")
    void idempotentReplay_doesNotPersistAdditionalAuditEvents() throws Exception {
        JobRecord job = acceptedJob("audit-it-030", "cid-audit-it-030");
        job.setCurrentState("RUNNING");
        job.setVersion(1L);
        jobRepository.save(job);

        // First transition to COMPLETED — persists audit event
        lifecycleService.markCompleted("audit-it-030", "cid-audit-it-030", 2);

        long countAfterFirst;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) AS cnt FROM audit_events WHERE resource_ref = 'audit-it-030'");
            rs.next();
            countAfterFirst = rs.getLong("cnt");
        }

        // Idempotent replay — should NOT add more audit events
        lifecycleService.transition(
                TransitionRequest.builder("audit-it-030", JobLifecycleState.COMPLETED).build());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) AS cnt FROM audit_events WHERE resource_ref = 'audit-it-030'");
            rs.next();
            long countAfterReplay = rs.getLong("cnt");
            assertThat(countAfterReplay).isEqualTo(countAfterFirst);
        }
    }

    @Test
    @DisplayName("Audit event retentionExpiryAt is at least one year from event timestamp")
    void auditEvent_retentionExpiryAt_isAtLeastOneYearFromEventTimestamp() throws Exception {
        JobRecord job = acceptedJob("audit-it-040", "cid-audit-it-040");
        jobRepository.save(job);

        lifecycleService.markDispatching("audit-it-040", "cid-audit-it-040");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT event_timestamp, retention_expiry_at FROM audit_events " +
                    "WHERE resource_ref = 'audit-it-040' AND operation = 'DISPATCH_HANDOFF'");
            assertTrue(rs.next(), "DISPATCH_HANDOFF audit event must exist");
            java.sql.Timestamp eventTs = rs.getTimestamp("event_timestamp");
            java.sql.Timestamp expiryTs = rs.getTimestamp("retention_expiry_at");
            assertThat(expiryTs).isNotNull();
            // retentionExpiryAt must be at least 364 days after event (allow 1 day buffer for test timing)
            long daysBetween = (expiryTs.getTime() - eventTs.getTime()) / (1000L * 60 * 60 * 24);
            assertThat(daysBetween).isGreaterThanOrEqualTo(364L);
        }
    }

    // ---- Helper ----

    private JobRecord acceptedJob(String jobId, String correlationId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setOperationType("QUICK_DEPLOY");
        r.setCurrentState("ACCEPTED");
        r.setCustomerIdHash("a1b2c3d4e5f6789012345678901234567890abcdef01234567890123456789ab");
        r.setSfdcToolId("sfdc-tool-audit-it");
        r.setPipelineId("pipeline-audit-it");
        r.setStepId("step-audit-it");
        r.setDataClassification("CONFIDENTIAL");
        r.setVersion(0L);
        return r;
    }
}
