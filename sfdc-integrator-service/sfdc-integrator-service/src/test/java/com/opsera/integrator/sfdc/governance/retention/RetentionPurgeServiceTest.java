package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RetentionPurgeService}.
 *
 * <p>Covers AC-2 (eligibility filtering), AC-1/AC-5 (dry-run/enforce modes),
 * AC-3 (legal hold protection), AC-5 (batch limits), and AC-6 (failure handling).
 */
@ExtendWith(MockitoExtension.class)
class RetentionPurgeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-25T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private RetentionMetadataRepository retentionRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private AuditEventWriter auditWriter;

    @Mock
    private SafeStructuredLogger safeLogger;

    private RetentionPurgeProperties purgeProperties;
    private RetentionPurgeService service;

    @BeforeEach
    void setUp() {
        purgeProperties = new RetentionPurgeProperties();
        purgeProperties.setEnabled(true);
        purgeProperties.setMode("DRY_RUN");
        purgeProperties.setBatchSize(100);
        purgeProperties.setMaxRuntimeSeconds(300);

        service = new RetentionPurgeService(
                retentionRepository, jobRepository, auditWriter, safeLogger,
                purgeProperties, FIXED_CLOCK);
    }

    // ---- AC-1: DISABLED mode exits immediately ----

    @Test
    void runPurge_disabledMode_returnsImmediatelyWithoutQuery() {
        PurgeRunResult result = service.runPurge(PurgeMode.DISABLED, 100);

        assertThat(result.getMode()).isEqualTo(PurgeMode.DISABLED);
        assertThat(result.getRunId()).isEqualTo("disabled");
        verifyNoInteractions(retentionRepository, jobRepository);
    }

    @Test
    void runPurge_nullMode_treatedAsDisabled() {
        PurgeRunResult result = service.runPurge(null, 100);

        assertThat(result.getMode()).isEqualTo(PurgeMode.DISABLED);
        verifyNoInteractions(retentionRepository, jobRepository);
    }

    // ---- AC-1: DRY_RUN mode selects but does not mutate ----

    @Test
    void runPurge_dryRunMode_selectsCandidatesWithoutErasing() {
        List<RetentionMetadata> candidates = List.of(
                eligibleCandidate("job-001"),
                eligibleCandidate("job-002"));
        when(retentionRepository.findPurgeCandidates(any(), anyInt())).thenReturn(candidates);

        PurgeRunResult result = service.runPurge(PurgeMode.DRY_RUN, 100);

        assertThat(result.getMode()).isEqualTo(PurgeMode.DRY_RUN);
        assertThat(result.getSelectedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(2);
        assertThat(result.getPurgedCount()).isEqualTo(0);

        // DRY_RUN must NOT erase job data or mark as purged
        verify(jobRepository, never()).eraseJobData(anyString());
        verify(retentionRepository, never()).markPurged(anyString(), any());
    }

    @Test
    void runPurge_dryRunMode_emitsAuditEvents() {
        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(eligibleCandidate("job-001")));

        service.runPurge(PurgeMode.DRY_RUN, 100);

        // Audit events: run start + batch result + run complete = at least 2 writes
        verify(auditWriter, atLeast(2)).write(any(), anyString(), any(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void runPurge_dryRunMode_noCandidates_returnsZeroCounts() {
        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        PurgeRunResult result = service.runPurge(PurgeMode.DRY_RUN, 100);

        assertThat(result.getSelectedCount()).isEqualTo(0);
        assertThat(result.getPurgedCount()).isEqualTo(0);
    }

    // ---- AC-2: ENFORCE mode erases eligible records ----

    @Test
    void runPurge_enforceMode_erasesEligibleRecords() {
        List<RetentionMetadata> candidates = List.of(eligibleCandidate("job-001"));
        when(retentionRepository.findPurgeCandidates(any(), anyInt())).thenReturn(candidates);
        when(retentionRepository.markPurged(anyString(), any())).thenReturn(1);

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.getPurgedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);

        verify(jobRepository).eraseJobData("job-001");
        verify(retentionRepository).markPurged(eq("job-001"), any());
    }

    @Test
    void runPurge_enforceMode_multipleRecords_purgesAll() {
        List<RetentionMetadata> candidates = List.of(
                eligibleCandidate("job-001"),
                eligibleCandidate("job-002"),
                eligibleCandidate("job-003"));
        when(retentionRepository.findPurgeCandidates(any(), anyInt())).thenReturn(candidates);
        when(retentionRepository.markPurged(anyString(), any())).thenReturn(1);

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.getPurgedCount()).isEqualTo(3);
        verify(jobRepository, times(3)).eraseJobData(anyString());
    }

    // ---- AC-3: Legal hold records are never purged ----

    @Test
    void runPurge_enforceMode_legalHoldCandidate_isSkipped() {
        RetentionMetadata legalHold = eligibleCandidate("job-hold-001");
        legalHold.setLegalHold(true);
        legalHold.setPurgeEligibilityStatus(PurgeEligibilityStatus.LEGAL_HOLD);

        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(legalHold));

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getPurgedCount()).isEqualTo(0);

        verify(jobRepository, never()).eraseJobData(anyString());
        verify(retentionRepository, never()).markPurged(anyString(), any());
    }

    @Test
    void runPurge_enforceMode_eligibleStatusChangedToIneligible_isSkipped() {
        RetentionMetadata changed = eligibleCandidate("job-changed-001");
        changed.setPurgeEligibilityStatus(PurgeEligibilityStatus.INELIGIBLE);

        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(changed));

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(jobRepository, never()).eraseJobData(anyString());
    }

    // ---- AC-5: Batch size limits ----

    @Test
    void runPurge_batchSizeLimit_cappedAt1000() {
        when(retentionRepository.findPurgeCandidates(any(), eq(1000)))
                .thenReturn(Collections.emptyList());

        service.runPurge(PurgeMode.DRY_RUN, 9999);

        // Verify the query was issued with the capped limit
        verify(retentionRepository).findPurgeCandidates(any(), eq(1000));
    }

    @Test
    void runPurge_batchSizeLimit_respectsConfiguredLimit() {
        when(retentionRepository.findPurgeCandidates(any(), eq(50)))
                .thenReturn(Collections.emptyList());

        service.runPurge(PurgeMode.DRY_RUN, 50);

        verify(retentionRepository).findPurgeCandidates(any(), eq(50));
    }

    // ---- AC-6: Failure handling — run continues on per-record errors ----

    @Test
    void runPurge_enforceMode_erasureFailure_countsAsFailedAndContinues() {
        List<RetentionMetadata> candidates = List.of(
                eligibleCandidate("job-fail-001"),
                eligibleCandidate("job-ok-001"));

        when(retentionRepository.findPurgeCandidates(any(), anyInt())).thenReturn(candidates);
        // First record throws, second succeeds
        doThrow(new LifecyclePersistenceException("simulated failure"))
                .when(jobRepository).eraseJobData("job-fail-001");
        when(retentionRepository.markPurged(eq("job-ok-001"), any())).thenReturn(1);

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getPurgedCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();

        // Second record must still be processed despite first failure
        verify(jobRepository).eraseJobData("job-ok-001");
    }

    @Test
    void runPurge_enforceMode_allFail_hasFailuresIsTrue() {
        List<RetentionMetadata> candidates = List.of(eligibleCandidate("job-fail-001"));
        when(retentionRepository.findPurgeCandidates(any(), anyInt())).thenReturn(candidates);
        doThrow(new RuntimeException("unexpected"))
                .when(jobRepository).eraseJobData(anyString());

        PurgeRunResult result = service.runPurge(PurgeMode.ENFORCE, 100);

        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    // ---- AC-5: Concurrent run lock prevents duplicate runs ----

    @Test
    void runPurge_concurrentRunAttempt_secondRunRejected() throws Exception {
        // Simulate a long-running first purge using a slow repo call
        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenAnswer(inv -> {
                    Thread.sleep(50);
                    return Collections.emptyList();
                });

        // Start first run in a background thread
        Thread firstRun = new Thread(() -> service.runPurge(PurgeMode.DRY_RUN, 100));
        firstRun.start();
        Thread.sleep(10); // let first run acquire lock

        PurgeRunResult rejected = service.runPurge(PurgeMode.DRY_RUN, 100);

        firstRun.join(2000);

        assertThat(rejected.getRunId()).isEqualTo("lock-rejected");
    }

    // ---- AC-4: Audit events emitted for run outcomes ----

    @Test
    void runPurge_enforceMode_emitsAuditEventsForRunAndBatch() {
        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(eligibleCandidate("job-001")));
        when(retentionRepository.markPurged(anyString(), any())).thenReturn(1);

        service.runPurge(PurgeMode.ENFORCE, 100);

        // run start + batch + run complete = at least 2 audit writes
        verify(auditWriter, atLeast(2)).write(any(), anyString(), any(), anyString(), any(), any(), anyString(), anyString());
    }

    // ---- Result shape ----

    @Test
    void runPurge_dryRunMode_resultHasRunIdAndTimestamps() {
        when(retentionRepository.findPurgeCandidates(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        PurgeRunResult result = service.runPurge(PurgeMode.DRY_RUN, 100);

        assertThat(result.getRunId()).isNotBlank();
        assertThat(result.getRunId()).isNotEqualTo("disabled");
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    // ---- Helpers ----

    private RetentionMetadata eligibleCandidate(String jobRef) {
        RetentionMetadata m = new RetentionMetadata();
        m.setJobRef(jobRef);
        m.setLegalHold(false);
        m.setPurgeEligibilityStatus(PurgeEligibilityStatus.ELIGIBLE);
        m.setRetentionExpiration(FIXED_NOW.minusSeconds(3600));
        return m;
    }
}
