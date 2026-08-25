package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RetentionMetadataService} (AC-4, AC-5).
 *
 * <p>Verifies: metadata assignment, audit event emission, legal hold behavior,
 * and fail-closed error handling.
 */
@ExtendWith(MockitoExtension.class)
class RetentionMetadataServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-06-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private RetentionMetadataRepository repository;

    @Mock
    private ClassificationPolicyResolver classificationResolver;

    @Mock
    private AuditEventWriter auditWriter;

    private RetentionMetadataService service;

    @BeforeEach
    void setUp() {
        RetentionPolicyProperties props = new RetentionPolicyProperties();
        Map<RetentionCategory, Long> durations = new EnumMap<>(RetentionCategory.class);
        durations.put(RetentionCategory.SHORT_TERM, 30L);
        durations.put(RetentionCategory.STANDARD, 365L);
        durations.put(RetentionCategory.EXTENDED, 2555L);
        durations.put(RetentionCategory.AUDIT_MANDATED, 3650L);
        props.setDurationDays(durations);

        RetentionPolicyResolver policyResolver = new RetentionPolicyResolver(props, FIXED_CLOCK);

        service = new RetentionMetadataService(policyResolver, repository,
                                               classificationResolver, auditWriter);
    }

    private ClassificationContext confidentialStandardCtx() {
        return new ClassificationContext(
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategoryHint.STANDARD,
                "test-assign");
    }

    private ClassificationContext restrictedExtendedCtx() {
        return new ClassificationContext(
                GovernanceDataCategory.SFDC_ARTIFACT,
                DataClassification.RESTRICTED,
                RetentionCategoryHint.EXTENDED,
                "test-assign-artifact");
    }

    // ---- assign ----

    @Test
    void assign_jobMetadata_persistsRetentionMetadata() {
        when(classificationResolver.resolve(any(), any())).thenReturn(confidentialStandardCtx());

        RetentionMetadata result = service.assign("job-001", null,
                GovernanceDataCategory.JOB_METADATA, "test-assign");

        verify(repository).save(any(RetentionMetadata.class));
        assertThat(result.getJobRef()).isEqualTo("job-001");
        assertThat(result.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(result.getRetentionCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(result.isLegalHold()).isFalse();
        assertThat(result.getPurgeEligibilityStatus()).isEqualTo(PurgeEligibilityStatus.INELIGIBLE);
    }

    @Test
    void assign_sfdcArtifact_usesExtendedCategory() {
        when(classificationResolver.resolve(any(), any())).thenReturn(restrictedExtendedCtx());

        RetentionMetadata result = service.assign("artifact-001", "pipeline-fixture-abc",
                GovernanceDataCategory.SFDC_ARTIFACT, "test-assign-artifact");

        assertThat(result.getRetentionCategory()).isEqualTo(RetentionCategory.EXTENDED);
        assertThat(result.getClassification()).isEqualTo(DataClassification.RESTRICTED);
        assertThat(result.getArtifactRef()).isEqualTo("pipeline-fixture-abc");
    }

    @Test
    void assign_emitsRetentionMetadataAssignedAuditEvent() {
        when(classificationResolver.resolve(any(), any())).thenReturn(confidentialStandardCtx());

        service.assign("job-audit-001", null,
                GovernanceDataCategory.JOB_METADATA, "test-audit");

        verify(auditWriter).write(
                eq("job-audit-001"),
                eq(AuditResourceType.RETENTION_RECORD),
                eq("job-audit-001"),
                eq(AuditOperation.RETENTION_METADATA_ASSIGNED),
                eq(DataClassification.CONFIDENTIAL),
                any(),
                eq("RetentionMetadataService.assign"));
    }

    @Test
    void assign_repositoryFailure_throwsRetentionAssignmentException() {
        when(classificationResolver.resolve(any(), any())).thenReturn(confidentialStandardCtx());
        doThrow(new RetentionAssignmentException("db error", "job-err", "PERSISTENCE_FAILURE"))
                .when(repository).save(any());

        assertThatThrownBy(() ->
                service.assign("job-err", null, GovernanceDataCategory.JOB_METADATA, "test"))
                .isInstanceOf(RetentionAssignmentException.class);
        verifyNoInteractions(auditWriter);
    }

    // ---- applyLegalHold ----

    @Test
    void applyLegalHold_true_setsPurgeStatusToLegalHold() {
        RetentionMetadata existing = buildExisting("job-hold-001",
                DataClassification.CONFIDENTIAL, false);
        when(repository.findByJobRef("job-hold-001")).thenReturn(Optional.of(existing));

        service.applyLegalHold("job-hold-001", true);

        verify(repository).updateLegalHold(
                eq("job-hold-001"),
                eq(true),
                eq(PurgeEligibilityStatus.LEGAL_HOLD));
    }

    @Test
    void applyLegalHold_false_recomputesEligibility() {
        RetentionMetadata existing = buildExisting("job-hold-002",
                DataClassification.CONFIDENTIAL, true);
        // Retention expiration in the future — releasing hold should make it INELIGIBLE
        existing.setRetentionExpiration(FIXED_NOW.plusSeconds(86400 * 365));
        when(repository.findByJobRef("job-hold-002")).thenReturn(Optional.of(existing));

        service.applyLegalHold("job-hold-002", false);

        verify(repository).updateLegalHold(
                eq("job-hold-002"),
                eq(false),
                eq(PurgeEligibilityStatus.INELIGIBLE));
    }

    @Test
    void applyLegalHold_emitsLegalHoldAppliedAuditEvent() {
        RetentionMetadata existing = buildExisting("job-hold-003",
                DataClassification.CONFIDENTIAL, false);
        when(repository.findByJobRef("job-hold-003")).thenReturn(Optional.of(existing));

        service.applyLegalHold("job-hold-003", true);

        verify(auditWriter).write(
                eq("job-hold-003"),
                eq(AuditResourceType.RETENTION_RECORD),
                eq("job-hold-003"),
                eq(AuditOperation.LEGAL_HOLD_APPLIED),
                eq(DataClassification.CONFIDENTIAL),
                any(),
                eq("RetentionMetadataService.applyLegalHold"));
    }

    @Test
    void applyLegalHold_recordNotFound_throwsRetentionAssignmentException() {
        when(repository.findByJobRef("job-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyLegalHold("job-missing", true))
                .isInstanceOf(RetentionAssignmentException.class)
                .hasMessageContaining("RECORD_NOT_FOUND")
                .extracting(e -> ((RetentionAssignmentException) e).getFailureCategory())
                .isEqualTo("RECORD_NOT_FOUND");
    }

    @Test
    void applyLegalHold_legalHoldOverridesEvenWhenExpirationPassed() {
        RetentionMetadata existing = buildExisting("job-hold-004",
                DataClassification.RESTRICTED, false);
        // Expiration in the past — but applying hold should give LEGAL_HOLD, not ELIGIBLE
        existing.setRetentionExpiration(FIXED_NOW.minusSeconds(86400));
        when(repository.findByJobRef("job-hold-004")).thenReturn(Optional.of(existing));

        service.applyLegalHold("job-hold-004", true);

        verify(repository).updateLegalHold(
                eq("job-hold-004"),
                eq(true),
                eq(PurgeEligibilityStatus.LEGAL_HOLD));
    }

    // ---- Helpers ----

    private static RetentionMetadata buildExisting(String jobRef,
                                                    DataClassification classification,
                                                    boolean legalHold) {
        RetentionMetadata m = new RetentionMetadata();
        m.setJobRef(jobRef);
        m.setClassification(classification);
        m.setRetentionCategory(RetentionCategory.STANDARD);
        m.setRetentionStart(FIXED_NOW.minusSeconds(86400 * 30));
        m.setRetentionExpiration(FIXED_NOW.plusSeconds(86400 * 335));
        m.setLegalHold(legalHold);
        m.setPurgeEligibilityStatus(legalHold
                ? PurgeEligibilityStatus.LEGAL_HOLD
                : PurgeEligibilityStatus.INELIGIBLE);
        return m;
    }
}
