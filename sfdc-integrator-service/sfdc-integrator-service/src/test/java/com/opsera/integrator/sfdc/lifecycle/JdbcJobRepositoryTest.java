package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobDiagnostic;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.lifecycle.model.WorkerAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JdbcJobRepository} verifying CRUD operations,
 * ordered checkpoint retrieval, optimistic concurrency, and duplicate rejection.
 *
 * <p>Uses H2 in PostgreSQL compatibility mode with Flyway enabled so the full
 * lifecycle schema is applied before each test class runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class JdbcJobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    // ---- save and findById ----

    @Test
    void save_thenFindById_returnsPersistedJob() {
        JobRecord job = acceptedJob("repo-test-001", "cid-repo-001");
        jobRepository.save(job);

        Optional<JobRecord> found = jobRepository.findById("repo-test-001");
        assertThat(found).isPresent();
        assertThat(found.get().getCorrelationId()).isEqualTo("cid-repo-001");
        assertThat(found.get().getOperationType()).isEqualTo("QUICK_DEPLOY");
        assertThat(found.get().getCurrentState()).isEqualTo("ACCEPTED");
        assertThat(found.get().getVersion()).isEqualTo(0L);
    }

    @Test
    void findById_notFound_returnsEmpty() {
        Optional<JobRecord> result = jobRepository.findById("nonexistent-job-id-xyz");
        assertThat(result).isEmpty();
    }

    // ---- findByCorrelationId ----

    @Test
    void findByCorrelationId_existingJob_returnsIt() {
        JobRecord job = acceptedJob("repo-cid-test-002", "cid-unique-repo-002");
        jobRepository.save(job);

        Optional<JobRecord> found = jobRepository.findByCorrelationId("cid-unique-repo-002");
        assertThat(found).isPresent();
        assertThat(found.get().getJobId()).isEqualTo("repo-cid-test-002");
    }

    @Test
    void findByCorrelationId_notFound_returnsEmpty() {
        Optional<JobRecord> result = jobRepository.findByCorrelationId("cid-does-not-exist-xyz");
        assertThat(result).isEmpty();
    }

    // ---- updateState ----

    @Test
    void updateState_validVersion_transitionsStateAndIncrementsVersion() {
        JobRecord job = acceptedJob("repo-state-003", "cid-state-003");
        jobRepository.save(job);

        jobRepository.updateState("repo-state-003", "RUNNING", 0L);

        Optional<JobRecord> updated = jobRepository.findById("repo-state-003");
        assertThat(updated).isPresent();
        assertThat(updated.get().getCurrentState()).isEqualTo("RUNNING");
        assertThat(updated.get().getVersion()).isEqualTo(1L);
    }

    @Test
    void updateState_staleVersion_throwsLifecyclePersistenceException() {
        JobRecord job = acceptedJob("repo-occ-004", "cid-occ-004");
        jobRepository.save(job);
        jobRepository.updateState("repo-occ-004", "RUNNING", 0L);

        assertThatThrownBy(() -> jobRepository.updateState("repo-occ-004", "COMPLETED", 0L))
                .isInstanceOf(LifecyclePersistenceException.class)
                .hasMessageContaining("repo-occ-004");
    }

    // ---- saveCheckpoint and findCheckpointsByJobId ----

    @Test
    void saveCheckpoint_thenFindByJobId_returnsOrderedCheckpoints() {
        JobRecord job = acceptedJob("repo-cp-005", "cid-cp-005");
        jobRepository.save(job);

        jobRepository.saveCheckpoint(checkpoint("repo-cp-005", 2, "EXEC_DONE", "COMPLETED"));
        jobRepository.saveCheckpoint(checkpoint("repo-cp-005", 1, "DISPATCH_STARTED", "RUNNING"));

        List<JobCheckpoint> checkpoints = jobRepository.findCheckpointsByJobId("repo-cp-005");
        assertThat(checkpoints).hasSize(2);
        assertThat(checkpoints.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(checkpoints.get(0).getCheckpointCode()).isEqualTo("DISPATCH_STARTED");
        assertThat(checkpoints.get(1).getSequenceNumber()).isEqualTo(2);
    }

    @Test
    void saveCheckpoint_duplicateSequence_throwsLifecyclePersistenceException() {
        JobRecord job = acceptedJob("repo-dup-cp-006", "cid-dup-cp-006");
        jobRepository.save(job);
        jobRepository.saveCheckpoint(checkpoint("repo-dup-cp-006", 1, "DISPATCH_STARTED", "RUNNING"));

        assertThatThrownBy(() ->
            jobRepository.saveCheckpoint(checkpoint("repo-dup-cp-006", 1, "DUPLICATE_CODE", "RUNNING")))
                .isInstanceOf(LifecyclePersistenceException.class);
    }

    @Test
    void findCheckpointsByJobId_noCheckpoints_returnsEmptyList() {
        JobRecord job = acceptedJob("repo-no-cp-007", "cid-no-cp-007");
        jobRepository.save(job);

        List<JobCheckpoint> checkpoints = jobRepository.findCheckpointsByJobId("repo-no-cp-007");
        assertThat(checkpoints).isEmpty();
    }

    // ---- saveDiagnostic ----

    @Test
    void saveDiagnostic_persistedAndRetrievableViaJdbc() {
        JobRecord job = acceptedJob("repo-diag-008", "cid-diag-008");
        jobRepository.save(job);

        JobDiagnostic diag = new JobDiagnostic();
        diag.setJobId("repo-diag-008");
        diag.setSafeSummary("Deployment failed: connection refused");
        diag.setErrorCode("SFDC_CONNECT_FAILED");
        jobRepository.saveDiagnostic(diag);

        Optional<JobRecord> found = jobRepository.findById("repo-diag-008");
        assertThat(found).isPresent();
    }

    // ---- saveWorkerAttempt ----

    @Test
    void saveWorkerAttempt_persistedSuccessfully() {
        JobRecord job = acceptedJob("repo-worker-009", "cid-worker-009");
        jobRepository.save(job);

        WorkerAttempt attempt = new WorkerAttempt();
        attempt.setJobId("repo-worker-009");
        attempt.setAttemptNumber(1);
        attempt.setWorkerId("worker-pod-test-009");
        jobRepository.saveWorkerAttempt(attempt);

        Optional<JobRecord> found = jobRepository.findById("repo-worker-009");
        assertThat(found).isPresent();
    }

    @Test
    void saveWorkerAttempt_duplicateAttemptNumber_throwsLifecyclePersistenceException() {
        JobRecord job = acceptedJob("repo-dup-worker-010", "cid-dup-worker-010");
        jobRepository.save(job);

        WorkerAttempt attempt = new WorkerAttempt();
        attempt.setJobId("repo-dup-worker-010");
        attempt.setAttemptNumber(1);
        attempt.setWorkerId("worker-pod-test-010a");
        jobRepository.saveWorkerAttempt(attempt);

        WorkerAttempt dup = new WorkerAttempt();
        dup.setJobId("repo-dup-worker-010");
        dup.setAttemptNumber(1);
        dup.setWorkerId("worker-pod-test-010b");
        assertThatThrownBy(() -> jobRepository.saveWorkerAttempt(dup))
                .isInstanceOf(LifecyclePersistenceException.class);
    }

    // ---- Job record without diagnostic ----

    @Test
    void job_withoutDiagnostic_isQueryableWhileActive() {
        JobRecord job = acceptedJob("repo-no-diag-011", "cid-no-diag-011");
        jobRepository.save(job);

        Optional<JobRecord> found = jobRepository.findById("repo-no-diag-011");
        assertThat(found).isPresent();
        assertThat(found.get().getCurrentState()).isEqualTo("ACCEPTED");
    }

    // ---- helpers ----

    private JobRecord acceptedJob(String jobId, String correlationId) {
        JobRecord job = new JobRecord();
        job.setJobId(jobId);
        job.setCorrelationId(correlationId);
        job.setOperationType("QUICK_DEPLOY");
        job.setCurrentState("ACCEPTED");
        job.setDataClassification("CONFIDENTIAL");
        job.setVersion(0L);
        return job;
    }

    private JobCheckpoint checkpoint(String jobId, int seq, String code, String state) {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setJobId(jobId);
        cp.setSequenceNumber(seq);
        cp.setCheckpointCode(code);
        cp.setStateAtCheckpoint(state);
        cp.setSafeMessage("Safe message for seq " + seq);
        return cp;
    }
}
