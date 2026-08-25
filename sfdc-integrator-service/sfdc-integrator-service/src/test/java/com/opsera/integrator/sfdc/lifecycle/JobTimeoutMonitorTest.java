package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobTimeoutMonitor} using mocked collaborators and a fixed clock.
 * Verifies candidate selection, grace-period protection, terminal-state skipping,
 * concurrency safety, and metrics emission (AC-1, AC-3, AC-5).
 */
class JobTimeoutMonitorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    private JobTimeoutProperties properties;
    private JobRepository jobRepository;
    private JobLifecycleService lifecycleService;
    private JobDiagnosticsService diagnosticsService;
    private SimpleMeterRegistry meterRegistry;
    private JobTimeoutMonitor monitor;

    @BeforeEach
    void setUp() {
        properties = new JobTimeoutProperties();
        jobRepository = mock(JobRepository.class);
        lifecycleService = mock(JobLifecycleService.class);
        diagnosticsService = mock(JobDiagnosticsService.class);
        meterRegistry = new SimpleMeterRegistry();
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        monitor = new JobTimeoutMonitor(properties, jobRepository, lifecycleService,
                diagnosticsService, meterRegistry, fixedClock);

        when(diagnosticsService.buildSafeSummary(any(), any())).thenReturn("safe-summary");
        when(jobRepository.findCheckpointsByJobId(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void runScan_disabled_doesNotQueryRepository() {
        properties.setEnabled(false);
        monitor.runScan();
        verify(jobRepository, never()).findStaleActiveJobs(any(), anyInt());
    }

    @Test
    void runScan_noStaleJobs_noFinalizationCalls() {
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(Collections.emptyList());
        monitor.runScan();
        verify(lifecycleService, never()).markTimedOut(any(), any(), any());
    }

    @Test
    void runScan_staleRunningJob_isFinalized() {
        JobRecord stale = runningJob("job-stale-001", "cid-001", "RUNNING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));

        monitor.runScan();

        verify(lifecycleService).markTimedOut(eq("job-stale-001"), eq("cid-001"), anyString());
    }

    @Test
    void runScan_staleDispatchingJob_isFinalized() {
        JobRecord stale = runningJob("job-dispatching-002", "cid-002", "DISPATCHING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));

        monitor.runScan();

        verify(lifecycleService).markTimedOut(eq("job-dispatching-002"), eq("cid-002"), anyString());
    }

    @Test
    void runScan_staleAcceptedJob_outsideGrace_isFinalized() {
        // ACCEPTED job created >30 min ago (beyond grace period)
        Instant createdAt = FIXED_NOW.minusSeconds(300 * 60); // 300 min ago
        JobRecord stale = runningJob("job-accepted-old-003", "cid-003", "ACCEPTED", staleTime());
        stale.setCreatedAt(createdAt);
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));

        monitor.runScan();

        verify(lifecycleService).markTimedOut(eq("job-accepted-old-003"), eq("cid-003"), anyString());
    }

    @Test
    void runScan_acceptedJobWithinGracePeriod_isSkipped() {
        // ACCEPTED job created 10 min ago — within the 30-min grace period
        Instant createdAt = FIXED_NOW.minusSeconds(10 * 60);
        JobRecord fresh = runningJob("job-accepted-fresh-004", "cid-004", "ACCEPTED", staleTime());
        fresh.setCreatedAt(createdAt);
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(fresh));

        monitor.runScan();

        verify(lifecycleService, never()).markTimedOut(any(), any(), any());
    }

    @Test
    void runScan_concurrencyConflict_isSkippedSafely() {
        JobRecord stale = runningJob("job-concurrent-005", "cid-005", "RUNNING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));
        doThrow(new LifecycleConcurrencyException("job-concurrent-005", 1L))
                .when(lifecycleService).markTimedOut(any(), any(), any());

        // Must not throw
        monitor.runScan();
    }

    @Test
    void runScan_alreadyTerminal_transitionExceptionIsSkippedSafely() {
        JobRecord stale = runningJob("job-terminal-006", "cid-006", "RUNNING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));
        doThrow(new LifecycleTransitionException("already terminal"))
                .when(lifecycleService).markTimedOut(any(), any(), any());

        // Must not throw
        monitor.runScan();
    }

    @Test
    void runScan_perJobError_doesNotAbortRemainingJobs() {
        JobRecord failing = runningJob("job-error-007", "cid-007", "RUNNING", staleTime());
        JobRecord ok = runningJob("job-ok-008", "cid-008", "RUNNING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(failing, ok));
        doThrow(new RuntimeException("unexpected"))
                .when(lifecycleService).markTimedOut(eq("job-error-007"), any(), any());

        monitor.runScan();

        verify(lifecycleService).markTimedOut(eq("job-ok-008"), any(), any());
    }

    @Test
    void runScan_queryFails_doesNotThrow() {
        when(jobRepository.findStaleActiveJobs(any(), anyInt()))
                .thenThrow(new RuntimeException("DB down"));

        // Must not propagate — monitor must not prevent next scan
        monitor.runScan();
    }

    @Test
    void runScan_emitsMetrics_onFinalization() {
        JobRecord stale = runningJob("job-metric-009", "cid-009", "RUNNING", staleTime());
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(List.of(stale));

        monitor.runScan();

        double count = meterRegistry.counter(JobTimeoutMonitor.METRIC_FINALIZED, "operationType", "UNKNOWN").count();
        assertTrue(count >= 1.0, "Finalized metric must be incremented");
    }

    @Test
    void runScan_emitsScanTotalMetric() {
        when(jobRepository.findStaleActiveJobs(any(), anyInt())).thenReturn(Collections.emptyList());

        monitor.runScan();

        double count = meterRegistry.counter(JobTimeoutMonitor.METRIC_SCAN_TOTAL, "result", "ok").count();
        assertTrue(count >= 1.0, "Scan total metric must be incremented");
    }

    // ---- helpers ----

    private JobRecord runningJob(String jobId, String correlationId, String state, Instant updatedAt) {
        JobRecord job = new JobRecord();
        job.setJobId(jobId);
        job.setCorrelationId(correlationId);
        job.setCurrentState(state);
        job.setOperationType("DEPLOY");
        job.setCreatedAt(FIXED_NOW.minusSeconds(300 * 60));
        job.setUpdatedAt(updatedAt);
        job.setVersion(1L);
        return job;
    }

    private Instant staleTime() {
        return FIXED_NOW.minusSeconds(300 * 60);
    }
}
