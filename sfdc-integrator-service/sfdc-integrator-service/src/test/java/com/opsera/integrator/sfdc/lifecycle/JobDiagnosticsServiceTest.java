package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobDiagnosticsService} using a fixed clock.
 * Verifies safe summary generation, allow-listed fields, and edge cases (AC-2, AC-5).
 */
class JobDiagnosticsServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private JobDiagnosticsService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new JobDiagnosticsService(fixedClock);
    }

    @Test
    void buildSafeSummary_includesOperationType() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(3600));
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("operationType=DEPLOY"));
    }

    @Test
    void buildSafeSummary_includesCurrentState() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(3600));
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("state=RUNNING"));
    }

    @Test
    void buildSafeSummary_elapsedSecondsCalculatedFromCreatedAt() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(600));
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("elapsedSeconds=600"));
    }

    @Test
    void buildSafeSummary_noCheckpoints_lastCheckpointCodeNone() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("lastCheckpointCode=NONE"));
    }

    @Test
    void buildSafeSummary_withCheckpoints_lastCheckpointCodeReturned() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        List<JobCheckpoint> checkpoints = List.of(
                checkpoint("WORKER_EXECUTION_STARTED", 1),
                checkpoint("DEPLOY_STAGE_IN_PROGRESS", 2)
        );
        String summary = service.buildSafeSummary(job, checkpoints);
        assertTrue(summary.contains("lastCheckpointCode=DEPLOY_STAGE_IN_PROGRESS"));
    }

    @Test
    void buildSafeSummary_failingStageNone_whenNoFailureCheckpoint() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        List<JobCheckpoint> checkpoints = List.of(checkpoint("WORKER_EXECUTION_STARTED", 1));
        String summary = service.buildSafeSummary(job, checkpoints);
        assertTrue(summary.contains("failingStage=NONE"));
    }

    @Test
    void buildSafeSummary_failingStageDetected_whenCheckpointContainsFail() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        List<JobCheckpoint> checkpoints = List.of(
                checkpoint("WORKER_EXECUTION_STARTED", 1),
                checkpoint("DEPLOY_STAGE_FAILED", 2)
        );
        String summary = service.buildSafeSummary(job, checkpoints);
        assertTrue(summary.contains("failingStage=DEPLOY_STAGE_FAILED"));
    }

    @Test
    void buildSafeSummary_includesCorrelationId() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        job.setCorrelationId("cid-test-001");
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("correlationId=cid-test-001"));
    }

    @Test
    void buildSafeSummary_nullCreatedAt_elapsedNegativeOne() {
        JobRecord job = runningJob("DEPLOY", null);
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertTrue(summary.contains("elapsedSeconds=-1"));
    }

    @Test
    void buildSafeSummary_deployOperation_retryEligible() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        List<JobCheckpoint> checkpoints = List.of(checkpoint("WORKER_EXECUTION_STARTED", 1));
        String summary = service.buildSafeSummary(job, checkpoints);
        assertTrue(summary.contains("retryEligibility=ELIGIBLE_IF_IDEMPOTENT"));
    }

    @Test
    void buildSafeSummary_quickDeployOperation_retryEligible() {
        JobRecord job = runningJob("QUICK_DEPLOY", FIXED_NOW.minusSeconds(100));
        List<JobCheckpoint> checkpoints = List.of(checkpoint("WORKER_EXECUTION_STARTED", 1));
        String summary = service.buildSafeSummary(job, checkpoints);
        assertTrue(summary.contains("retryEligibility=ELIGIBLE_IF_IDEMPOTENT"));
    }

    @Test
    void buildSafeSummary_noStackTraces_inSummary() {
        JobRecord job = runningJob("DEPLOY", FIXED_NOW.minusSeconds(100));
        String summary = service.buildSafeSummary(job, Collections.emptyList());
        assertFalse(summary.toLowerCase().contains("exception"),
                "Summary must not include exception stack trace fragments");
        assertFalse(summary.toLowerCase().contains("at com."),
                "Summary must not include Java stack frames");
    }

    // ---- helpers ----

    private JobRecord runningJob(String operationType, Instant createdAt) {
        JobRecord job = new JobRecord();
        job.setJobId("job-diag-test-001");
        job.setCorrelationId("cid-diag-test-001");
        job.setOperationType(operationType);
        job.setCurrentState("RUNNING");
        job.setCreatedAt(createdAt);
        job.setVersion(1L);
        return job;
    }

    private JobCheckpoint checkpoint(String code, int seq) {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setCheckpointCode(code);
        cp.setSequenceNumber(seq);
        cp.setStateAtCheckpoint("RUNNING");
        return cp;
    }
}
