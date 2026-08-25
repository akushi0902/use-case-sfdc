package com.opsera.integrator.sfdc.governance.audit;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditEventWriter} covering event construction,
 * classification propagation, audit-disabled skip, and failure handling.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventWriterTest {

    @Mock
    private AuditEventRepository repository;

    private AuditEventWriter writerEnabled;
    private AuditEventWriter writerDisabled;

    @BeforeEach
    void setUp() {
        writerEnabled = new AuditEventWriter(repository, true);
        writerDisabled = new AuditEventWriter(repository, false);
    }

    // ---- enabled mode ----

    @Test
    void write_enabled_appendsEventToRepository() {
        writerEnabled.write(
                "pipeline-writer-001",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-001",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                "{\"pipelineId\":\"pipeline-writer-001\"}",
                "TestController.method");

        verify(repository).append(any(AuditEvent.class));
    }

    @Test
    void write_enabled_eventHasCorrectOperation() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                "pipeline-writer-002",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-002",
                AuditOperation.JOB_CANCELLED,
                DataClassification.CONFIDENTIAL,
                null,
                "TestController.cancel");

        verify(repository).append(captor.capture());
        AuditEvent captured = captor.getValue();

        assertThat(captured.getOperation()).isEqualTo(AuditOperation.JOB_CANCELLED);
        assertThat(captured.getResourceType()).isEqualTo(AuditResourceType.QUICK_DEPLOY_JOB);
        assertThat(captured.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(captured.getActorType()).isEqualTo(AuditActorType.APPLICATION);
        assertThat(captured.getRequestSource()).isEqualTo("TestController.cancel");
    }

    @Test
    void write_enabled_classificationPropagatedToEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                "pipeline-writer-003",
                AuditResourceType.DATA_MIGRATION,
                "pipeline-writer-003",
                AuditOperation.DATA_MIGRATION_INITIATED,
                DataClassification.RESTRICTED,
                null,
                "DataMigrationController.migrate");

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getClassification()).isEqualTo(DataClassification.RESTRICTED);
    }

    @Test
    void write_enabled_nullClassification_defaultsToConfidential() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                "pipeline-writer-004",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-004",
                AuditOperation.JOB_SUBMITTED,
                null,
                null,
                null);

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
    }

    @Test
    void write_enabled_eventIdIsGeneratedUuid() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                "pipeline-writer-005",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-005",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                null,
                null);

        verify(repository).append(captor.capture());
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertThat(captor.getValue().getEventId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void write_enabled_nullActorRef_fallsBackToCorrelationId() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                null,
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-006",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                null,
                null);

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getActorRef()).isNotBlank();
    }

    // ---- failure handling ----

    @Test
    void write_repositoryThrows_propagatesAuditWriteException() {
        doThrow(new AuditWriteException("evt-x", AuditOperation.JOB_SUBMITTED,
                "JDBC_INSERT_FAILED", new RuntimeException("db down")))
                .when(repository).append(any());

        assertThatThrownBy(() -> writerEnabled.write(
                "pipeline-writer-007",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-007",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                null,
                null))
            .isInstanceOf(AuditWriteException.class)
            .hasMessageContaining("JOB_SUBMITTED");
    }

    // ---- system actor overload ----

    @Test
    void write_systemActor_usesSystemActorType() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                AuditActorType.SYSTEM,
                "cid-lifecycle-001",
                AuditResourceType.LIFECYCLE_JOB,
                "job-lifecycle-001",
                AuditOperation.DISPATCH_HANDOFF,
                DataClassification.CONFIDENTIAL,
                "{\"jobId\":\"job-lifecycle-001\",\"fromState\":\"ACCEPTED\",\"toState\":\"DISPATCHING\"}",
                "JobLifecycleService.transition");

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(captor.getValue().getOperation()).isEqualTo(AuditOperation.DISPATCH_HANDOFF);
        assertThat(captor.getValue().getResourceType()).isEqualTo(AuditResourceType.LIFECYCLE_JOB);
    }

    @Test
    void write_systemActor_retentionExpiryAtIsSet() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                AuditActorType.SYSTEM,
                "cid-lifecycle-002",
                AuditResourceType.LIFECYCLE_JOB,
                "job-lifecycle-002",
                AuditOperation.JOB_TIMEOUT_FINALIZED,
                DataClassification.CONFIDENTIAL,
                null,
                "JobLifecycleService.transition");

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getRetentionExpiryAt()).isNotNull();
        // retentionExpiryAt must be after the event timestamp
        assertThat(captor.getValue().getRetentionExpiryAt())
                .isAfter(captor.getValue().getEventTimestamp());
    }

    @Test
    void write_applicationActor_backwardCompatible_usesApplicationActorType() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        writerEnabled.write(
                "pipeline-compat-001",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-compat-001",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                null,
                "TestController.method");

        verify(repository).append(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.APPLICATION);
    }

    @Test
    void computeRetentionExpiry_confidential_returnsOneYearFromEvent() {
        java.time.Instant now = java.time.Instant.parse("2024-01-01T10:00:00Z");
        java.time.Instant expiry = AuditEventWriter.computeRetentionExpiry(now, DataClassification.CONFIDENTIAL);
        assertThat(expiry).isEqualTo(java.time.Instant.parse("2025-01-01T10:00:00Z"));
    }

    @Test
    void computeRetentionExpiry_restricted_returnsSixYearsFromEvent() {
        java.time.Instant now = java.time.Instant.parse("2024-01-01T10:00:00Z");
        java.time.Instant expiry = AuditEventWriter.computeRetentionExpiry(now, DataClassification.RESTRICTED);
        // 7 * 365 = 2555 days from 2024-01-01 = approximately 2031-01-01
        assertThat(expiry).isAfter(java.time.Instant.parse("2030-01-01T00:00:00Z"));
    }

    // ---- disabled mode ----

    @Test
    void write_disabled_doesNotCallRepository() {
        writerDisabled.write(
                "pipeline-writer-008",
                AuditResourceType.QUICK_DEPLOY_JOB,
                "pipeline-writer-008",
                AuditOperation.JOB_SUBMITTED,
                DataClassification.CONFIDENTIAL,
                null,
                null);

        verify(repository, never()).append(any());
    }
}
