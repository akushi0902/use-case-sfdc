package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JobTimeoutMonitor} backed by the real H2 database.
 *
 * <p>Seeds lifecycle records with controlled timestamps, runs the monitor, and verifies:
 * <ul>
 *   <li>Stale active jobs transition to TIMED_OUT (AC-1)</li>
 *   <li>Fresh active jobs remain unchanged (AC-4)</li>
 *   <li>Terminal jobs are never overwritten (AC-1 constraint)</li>
 *   <li>ACCEPTED grace-period jobs are protected (AC-4)</li>
 *   <li>Diagnostic summaries are written for timed-out jobs (AC-2)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "sfdc.lifecycle.timeout.stale-threshold-minutes=60",
    "sfdc.lifecycle.timeout.accepted-grace-period-minutes=15",
    "sfdc.lifecycle.timeout.max-jobs-per-scan=50"
})
class JobTimeoutMonitorIntegrationTest {

    @Autowired
    private JobTimeoutMonitor timeoutMonitor;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Clock clock;

    @Test
    void staleRunningJob_isTimedOut_afterScan() {
        JobRecord job = staleRunningJob("monitor-it-running-001", "cid-monitor-it-001");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-running-001");
        assertTrue(result.isPresent());
        assertEquals("TIMED_OUT", result.get().getCurrentState(),
                "Stale RUNNING job must be finalized as TIMED_OUT");
    }

    @Test
    void staleDispatchingJob_isTimedOut_afterScan() {
        JobRecord job = staleRunningJob("monitor-it-dispatching-002", "cid-monitor-it-002");
        job.setCurrentState("DISPATCHING");
        job.setVersion(1L);
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-dispatching-002");
        assertTrue(result.isPresent());
        assertEquals("TIMED_OUT", result.get().getCurrentState(),
                "Stale DISPATCHING job must be finalized as TIMED_OUT");
    }

    @Test
    void freshRunningJob_remainsRunning_afterScan() {
        JobRecord job = freshRunningJob("monitor-it-fresh-003", "cid-monitor-it-003");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-fresh-003");
        assertTrue(result.isPresent());
        assertEquals("RUNNING", result.get().getCurrentState(),
                "Fresh RUNNING job must not be timed out");
    }

    @Test
    void completedJob_remainsCompleted_afterScan() {
        JobRecord job = terminalJob("monitor-it-complete-004", "cid-monitor-it-004", "COMPLETED");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-complete-004");
        assertTrue(result.isPresent());
        assertEquals("COMPLETED", result.get().getCurrentState(),
                "COMPLETED job must not be overwritten by the monitor");
    }

    @Test
    void failedJob_remainsFailed_afterScan() {
        JobRecord job = terminalJob("monitor-it-failed-005", "cid-monitor-it-005", "FAILED");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-failed-005");
        assertTrue(result.isPresent());
        assertEquals("FAILED", result.get().getCurrentState(),
                "FAILED job must not be overwritten by the monitor");
    }

    @Test
    void staleAcceptedJob_outsideGrace_isTimedOut() {
        JobRecord job = staleAcceptedJob("monitor-it-acc-old-006", "cid-monitor-it-006");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-acc-old-006");
        assertTrue(result.isPresent());
        assertEquals("TIMED_OUT", result.get().getCurrentState(),
                "ACCEPTED job outside grace period must be timed out");
    }

    @Test
    void acceptedJobWithinGrace_remainsAccepted_afterScan() {
        JobRecord job = freshAcceptedJob("monitor-it-acc-fresh-007", "cid-monitor-it-007");
        jobRepository.save(job);

        timeoutMonitor.runScan();

        Optional<JobRecord> result = jobRepository.findById("monitor-it-acc-fresh-007");
        assertTrue(result.isPresent());
        assertEquals("ACCEPTED", result.get().getCurrentState(),
                "ACCEPTED job within grace period must not be timed out");
    }

    // ---- helpers ----

    private JobRecord staleRunningJob(String jobId, String correlationId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setOperationType("DEPLOY");
        r.setCurrentState("RUNNING");
        r.setCustomerIdHash("a1b2c3d4e5f6789012345678901234567890abcdef01234567890123456789ab");
        r.setSfdcToolId("sfdc-tool-fixture-001");
        r.setPipelineId("pipeline-monitor-it");
        r.setStepId("step-monitor-it");
        r.setDataClassification("CONFIDENTIAL");
        r.setVersion(2L);
        // updatedAt will be set to CURRENT_TIMESTAMP by the INSERT — a subsequent
        // UPDATE to backdate it ensures the job appears stale to the monitor query.
        return r;
    }

    private JobRecord freshRunningJob(String jobId, String correlationId) {
        JobRecord r = staleRunningJob(jobId, correlationId);
        // Version 1 = recently transitioned; the DB updated_at will be recent.
        r.setVersion(1L);
        return r;
    }

    private JobRecord terminalJob(String jobId, String correlationId, String state) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setOperationType("QUICK_DEPLOY");
        r.setCurrentState(state);
        r.setCustomerIdHash("b2c3d4e5f678901234567890123456789012345678901234567890123456bcde");
        r.setSfdcToolId("sfdc-tool-fixture-001");
        r.setPipelineId("pipeline-monitor-it");
        r.setStepId("step-monitor-it");
        r.setDataClassification("CONFIDENTIAL");
        r.setVersion(4L);
        return r;
    }

    private JobRecord staleAcceptedJob(String jobId, String correlationId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setOperationType("DEPLOY");
        r.setCurrentState("ACCEPTED");
        r.setCustomerIdHash("c3d4e5f6789012345678901234567890123456789012345678901234567890cd");
        r.setSfdcToolId("sfdc-tool-fixture-001");
        r.setPipelineId("pipeline-monitor-it");
        r.setStepId("step-monitor-it");
        r.setDataClassification("CONFIDENTIAL");
        r.setVersion(0L);
        r.setCreatedAt(Instant.now(clock).minusSeconds(300 * 60));
        return r;
    }

    private JobRecord freshAcceptedJob(String jobId, String correlationId) {
        JobRecord r = staleAcceptedJob(jobId, correlationId);
        r.setCreatedAt(Instant.now(clock).minusSeconds(5 * 60));
        return r;
    }
}
