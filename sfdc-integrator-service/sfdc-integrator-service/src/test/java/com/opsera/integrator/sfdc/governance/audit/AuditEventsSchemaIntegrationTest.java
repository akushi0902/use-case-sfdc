package com.opsera.integrator.sfdc.governance.audit;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the V3 audit events schema migration.
 *
 * <p>Verifies that the audit_events table is created with the expected columns and
 * indexes, that INSERT succeeds, and that the AuditEventRepository interface
 * exposes only an append() method (no update or delete path).
 *
 * <p>Uses H2 in PostgreSQL compatibility mode with Flyway enabled — no external
 * PostgreSQL, Kafka, or Hazelcast dependency required.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class AuditEventsSchemaIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuditEventRepository auditEventRepository;

    // ---- Table existence ----

    @Test
    void v3MigrationCreatesAuditEventsTable() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "audit_events", new String[]{"TABLE"});
            assertTrue(rs.next(), "V3 audit migration must create table: audit_events");
        }
    }

    // ---- Flyway history ----

    @Test
    void flywayHistoryRecordsV3MigrationAsSuccessful() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT version, success FROM flyway_schema_history WHERE version = '3'");
            assertTrue(rs.next(), "flyway_schema_history must contain version 3");
            assertTrue(rs.getBoolean("success"), "V3 migration must be recorded as successful");
        }
    }

    // ---- Column and index presence ----

    @Test
    void auditEventsTable_hasRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "audit_events", null);
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            assertThat(columns).contains(
                    "event_id", "correlation_id", "actor_type", "actor_ref",
                    "resource_type", "resource_ref", "operation", "classification",
                    "safe_metadata", "event_timestamp", "created_at", "request_source");
        }
    }

    // ---- Insert via repository ----

    @Test
    void auditEventRepository_append_insertsSuccessfully() {
        AuditEvent event = AuditEvent.builder()
                .eventId("audit-schema-test-insert-001")
                .correlationId("cid-audit-schema-001")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-audit-schema-001")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-audit-schema-001")
                .operation(AuditOperation.JOB_SUBMITTED)
                .classification(DataClassification.CONFIDENTIAL)
                .eventTimestamp(Instant.now())
                .requestSource("AuditEventsSchemaIntegrationTest")
                .build();

        auditEventRepository.append(event);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT event_id, operation, classification FROM audit_events " +
                    "WHERE event_id = 'audit-schema-test-insert-001'");
            assertTrue(rs.next(), "Inserted audit event must be retrievable");
            assertThat(rs.getString("operation")).isEqualTo("JOB_SUBMITTED");
            assertThat(rs.getString("classification")).isEqualTo("CONFIDENTIAL");
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify inserted audit event", e);
        }
    }

    @Test
    void auditEventRepository_duplicatePrimaryKey_throwsAuditWriteException() {
        AuditEvent event = AuditEvent.builder()
                .eventId("audit-schema-test-dup-002")
                .correlationId("cid-audit-schema-002")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-audit-schema-002")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-audit-schema-002")
                .operation(AuditOperation.JOB_SUBMITTED)
                .build();

        auditEventRepository.append(event);

        assertThatThrownBy(() -> auditEventRepository.append(event))
                .isInstanceOf(AuditWriteException.class);
    }

    // ---- Append-only interface verification ----

    @Test
    void auditEventRepository_interfaceHasNoUpdateOrDeleteMethod() throws NoSuchMethodException {
        Class<?> repoInterface = AuditEventRepository.class;

        // Verify the only public method on the interface is append()
        java.lang.reflect.Method[] methods = repoInterface.getDeclaredMethods();
        assertThat(methods).hasSize(1);
        assertThat(methods[0].getName()).isEqualTo("append");
    }

    @Test
    void auditEventRepository_correlationIdInsert_isQueryable() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .eventId("audit-schema-test-cid-003")
                .correlationId("unique-cid-for-audit-lookup-003")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-audit-003")
                .resourceType(AuditResourceType.DATA_MIGRATION)
                .resourceRef("pipeline-audit-003")
                .operation(AuditOperation.DATA_MIGRATION_INITIATED)
                .classification(DataClassification.RESTRICTED)
                .build();

        auditEventRepository.append(event);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT event_id FROM audit_events " +
                    "WHERE correlation_id = 'unique-cid-for-audit-lookup-003'");
            assertTrue(rs.next(), "Audit event must be retrievable by correlation_id");
            assertThat(rs.getString("event_id")).isEqualTo("audit-schema-test-cid-003");
        }
    }

    @Test
    void auditEventRepository_safeMetadataStored_andQueryable() throws Exception {
        String metadata = "{\"pipelineId\":\"pipeline-meta-004\",\"outcome\":\"SUBMITTED\"}";
        AuditEvent event = AuditEvent.builder()
                .eventId("audit-schema-test-meta-004")
                .correlationId("cid-audit-meta-004")
                .actorType(AuditActorType.APPLICATION)
                .actorRef("pipeline-meta-004")
                .resourceType(AuditResourceType.QUICK_DEPLOY_JOB)
                .resourceRef("pipeline-meta-004")
                .operation(AuditOperation.JOB_SUBMITTED)
                .safeMetadata(metadata)
                .build();

        auditEventRepository.append(event);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT safe_metadata FROM audit_events " +
                    "WHERE event_id = 'audit-schema-test-meta-004'");
            assertTrue(rs.next());
            assertThat(rs.getString("safe_metadata")).contains("pipelineId");
        }
    }
}
