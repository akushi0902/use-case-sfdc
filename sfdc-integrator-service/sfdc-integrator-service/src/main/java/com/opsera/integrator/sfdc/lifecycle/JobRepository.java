package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobDiagnostic;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.lifecycle.model.WorkerAttempt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for durable lifecycle job persistence.
 *
 * <p>Implementations are decoupled from controllers so lifecycle logic
 * remains unit-testable with mocked collaborators.
 *
 * <p>All operations throw {@link LifecyclePersistenceException} on constraint
 * violations, connectivity failures, or optimistic concurrency conflicts.
 */
public interface JobRepository {

    /**
     * Persists a new job record. Throws if a job with the same {@code jobId} already exists.
     */
    void save(JobRecord job);

    /**
     * Returns the job record for the given job ID, or empty if not found.
     */
    Optional<JobRecord> findById(String jobId);

    /**
     * Returns the most recent job record matching the given correlation ID, or empty if none.
     */
    Optional<JobRecord> findByCorrelationId(String correlationId);

    /**
     * Transitions the job to {@code newState}, incrementing the version from
     * {@code expectedVersion} to {@code expectedVersion + 1}.
     *
     * @throws LifecyclePersistenceException if no row was updated (version mismatch or job missing)
     */
    void updateState(String jobId, String newState, long expectedVersion);

    /**
     * Appends a checkpoint to the job's ordered history.
     * Throws if the (jobId, sequenceNumber) combination already exists.
     */
    void saveCheckpoint(JobCheckpoint checkpoint);

    /**
     * Returns all checkpoints for the given job, ordered by sequence number ascending.
     */
    List<JobCheckpoint> findCheckpointsByJobId(String jobId);

    /**
     * Persists a terminal diagnostic summary for the job.
     */
    void saveDiagnostic(JobDiagnostic diagnostic);

    /**
     * Records a worker execution attempt for the given job.
     * Throws if the (jobId, attemptNumber) combination already exists.
     */
    void saveWorkerAttempt(WorkerAttempt attempt);

    /**
     * Erases sensitive fields in the job record as part of a GDPR/CCPA purge action.
     *
     * <p>The job row is NOT deleted (FK constraints from retention metadata prevent it).
     * Instead, all identifiable fields are nulled or replaced with the tombstone marker
     * {@code "PURGED"}. Child records (checkpoints, diagnostics, worker attempts) ARE
     * physically deleted. This constitutes documented cryptographic erasure semantics.
     *
     * @param jobId the job identifier to erase
     */
    void eraseJobData(String jobId);

    /**
     * Returns active jobs (ACCEPTED, DISPATCHING, or RUNNING) whose last state update
     * was before {@code staleBefore}, ordered by {@code updated_at} ascending.
     *
     * <p>Callers are responsible for applying per-state grace-period filtering on the
     * returned list (e.g. skipping freshly accepted jobs with no checkpoints).
     *
     * @param staleBefore upper-bound on {@code updated_at} for stale candidates
     * @param maxResults  maximum number of rows to return
     * @return stale active job records, may be empty
     */
    List<JobRecord> findStaleActiveJobs(Instant staleBefore, int maxResults);
}
