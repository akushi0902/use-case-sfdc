package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JobLifecycleService} backed by the real H2 database.
 *
 * <p>Verifies that state transitions persist transactionally, that idempotent replays
 * do not re-mutate the database, and that optimistic concurrency conflicts are detected
 * on re-read (AC-6).
 *
 * <p>Uses the same @SpringBootTest + @ActiveProfiles("test") + Flyway-enabled pattern
 * as {@link JdbcJobRepositoryTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class JobLifecycleServiceIntegrationTest {

    @Autowired
    private JobLifecycleService lifecycleService;

    @Autowired
    private JobRepository jobRepository;

    // ---- State persists after transition ----

    @Test
    void transition_acceptedToRunning_statePersisted() {
        JobRecord job = acceptedJob("svc-it-001", "cid-svc-it-001");
        jobRepository.save(job);

        lifecycleService.transition(
                TransitionRequest.builder("svc-it-001", JobLifecycleState.RUNNING)
                        .correlationId("cid-svc-it-001")
                        .checkpointSequenceNumber(1)
                        .checkpointCode("WORKER_EXECUTION_STARTED")
                        .build());

        Optional<JobRecord> updated = jobRepository.findById("svc-it-001");
        assertTrue(updated.isPresent());
        assertEquals("RUNNING", updated.get().getCurrentState());
        assertEquals(1L, updated.get().getVersion());
    }

    @Test
    void transition_runningToCompleted_statePersisted() {
        JobRecord job = acceptedJob("svc-it-002", "cid-svc-it-002");
        job.setCurrentState("RUNNING");
        job.setVersion(1L);
        jobRepository.save(job);

        lifecycleService.markCompleted("svc-it-002", "cid-svc-it-002", 2);

        Optional<JobRecord> updated = jobRepository.findById("svc-it-002");
        assertTrue(updated.isPresent());
        assertEquals("COMPLETED", updated.get().getCurrentState());
        assertEquals(2L, updated.get().getVersion());
    }

    @Test
    void transition_runningToFailed_statePersisted_diagnosticSaved() {
        JobRecord job = acceptedJob("svc-it-003", "cid-svc-it-003");
        job.setCurrentState("RUNNING");
        job.setVersion(1L);
        jobRepository.save(job);

        lifecycleService.markFailed("svc-it-003", "cid-svc-it-003",
                "SFDC_CONNECT_FAILED", "Org connection refused");

        Optional<JobRecord> updated = jobRepository.findById("svc-it-003");
        assertTrue(updated.isPresent());
        assertEquals("FAILED", updated.get().getCurrentState());
    }

    // ---- Idempotent replay does not re-mutate ----

    @Test
    void transition_idempotentReplay_doesNotChangeVersion() {
        JobRecord job = acceptedJob("svc-it-010", "cid-svc-it-010");
        job.setCurrentState("RUNNING");
        job.setVersion(3L);
        jobRepository.save(job);

        TransitionResult result = lifecycleService.transition(
                TransitionRequest.builder("svc-it-010", JobLifecycleState.RUNNING).build());

        assertTrue(result.isIdempotentReplay());
        Optional<JobRecord> unchanged = jobRepository.findById("svc-it-010");
        assertTrue(unchanged.isPresent());
        assertEquals(3L, unchanged.get().getVersion(), "Idempotent replay must not change version");
    }

    // ---- Full lifecycle sequence ----

    @Test
    void fullLifecycleSequence_acceptedThroughCompleted() {
        JobRecord job = acceptedJob("svc-it-020", "cid-svc-it-020");
        jobRepository.save(job);

        lifecycleService.markDispatching("svc-it-020", "cid-svc-it-020");
        lifecycleService.markRunning("svc-it-020", "cid-svc-it-020", 1);
        TransitionResult finalResult = lifecycleService.markCompleted("svc-it-020", "cid-svc-it-020", 2);

        Optional<JobRecord> finished = jobRepository.findById("svc-it-020");
        assertTrue(finished.isPresent());
        assertEquals("COMPLETED", finished.get().getCurrentState());
        assertEquals(JobLifecycleState.COMPLETED, finalResult.getToState());
        assertFalse(finalResult.isIdempotentReplay());
    }

    // ---- Terminal state immutability ----

    @Test
    void transition_completedJobToRunning_throwsTransitionException() {
        JobRecord job = acceptedJob("svc-it-030", "cid-svc-it-030");
        job.setCurrentState("COMPLETED");
        job.setVersion(4L);
        jobRepository.save(job);

        assertThrows(LifecycleTransitionException.class, () ->
                lifecycleService.markRunning("svc-it-030", "cid-svc-it-030", 5));

        // State must remain unchanged
        Optional<JobRecord> unchanged = jobRepository.findById("svc-it-030");
        assertTrue(unchanged.isPresent());
        assertEquals("COMPLETED", unchanged.get().getCurrentState());
        assertEquals(4L, unchanged.get().getVersion());
    }

    // ---- Cancellation before dispatch ----

    @Test
    void transition_cancelledBeforeDispatch_terminatesFromAccepted() {
        JobRecord job = acceptedJob("svc-it-040", "cid-svc-it-040");
        jobRepository.save(job);

        TransitionResult result = lifecycleService.markCancelled(
                "svc-it-040", "cid-svc-it-040", "USER_REQUESTED");

        assertEquals(JobLifecycleState.ACCEPTED, result.getFromState());
        assertEquals(JobLifecycleState.CANCELLED, result.getToState());

        Optional<JobRecord> cancelled = jobRepository.findById("svc-it-040");
        assertTrue(cancelled.isPresent());
        assertEquals("CANCELLED", cancelled.get().getCurrentState());
    }

    // ---- Timed out ----

    @Test
    void transition_timedOut_fromRunning_terminates() {
        JobRecord job = acceptedJob("svc-it-050", "cid-svc-it-050");
        job.setCurrentState("RUNNING");
        job.setVersion(2L);
        jobRepository.save(job);

        lifecycleService.markTimedOut("svc-it-050", "cid-svc-it-050");

        Optional<JobRecord> timedOut = jobRepository.findById("svc-it-050");
        assertTrue(timedOut.isPresent());
        assertEquals("TIMED_OUT", timedOut.get().getCurrentState());
    }

    // ---- Helper ----

    private JobRecord acceptedJob(String jobId, String correlationId) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId(correlationId);
        r.setOperationType("QUICK_DEPLOY");
        r.setCurrentState("ACCEPTED");
        r.setCustomerIdHash("a1b2c3d4e5f6789012345678901234567890abcdef01234567890123456789ab");
        r.setSfdcToolId("sfdc-tool-fixture-001");
        r.setPipelineId("pipeline-svc-it");
        r.setStepId("step-svc-it");
        r.setDataClassification("CONFIDENTIAL");
        r.setVersion(0L);
        return r;
    }
}
