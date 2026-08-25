package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobDiagnostic;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Coordinates lifecycle state transitions for durable release jobs.
 *
 * <p>This service is the single authoritative path for all lifecycle mutations.
 * Controllers, listeners, and processors must not update {@code currentState}
 * directly in the repository — they must call this service.
 *
 * <h3>Transition flow</h3>
 * <ol>
 *   <li>Load the job record from the repository.</li>
 *   <li>Check for idempotent replay (already in target state).</li>
 *   <li>Validate the transition through {@link LifecycleTransitionPolicy}.</li>
 *   <li>Update the durable state with optimistic concurrency.</li>
 *   <li>Append a checkpoint if a sequence number is provided.</li>
 *   <li>Persist a diagnostic summary if the target is a terminal failure state.</li>
 * </ol>
 *
 * <p>All log messages use correlation context only; raw payloads and credentials
 * are never logged.
 */
@Service
public class JobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(JobLifecycleService.class);

    /** Default reason code used when a failure transition carries no explicit code. */
    static final String DEFAULT_FAILURE_CODE = "UNSPECIFIED_FAILURE";

    private final JobRepository repository;
    private final LifecycleTransitionPolicy policy;

    public JobLifecycleService(JobRepository repository) {
        this.repository = repository;
        this.policy = new LifecycleTransitionPolicy();
    }

    /**
     * Applies a lifecycle transition to the given job.
     *
     * @param request the transition details; must not be null
     * @return the transition result, including whether this was an idempotent replay
     * @throws LifecycleTransitionException if the transition is not allowed
     * @throws LifecycleConcurrencyException if a concurrent modification was detected
     * @throws LifecyclePersistenceException if the record could not be loaded or saved
     */
    public TransitionResult transition(TransitionRequest request) {
        if (request == null) {
            throw new LifecycleTransitionException("TransitionRequest must not be null");
        }

        String jobId = request.getJobId();
        JobLifecycleState targetState = request.getTargetState();

        JobRecord job = loadJob(jobId);
        JobLifecycleState currentState = JobLifecycleState.fromString(job.getCurrentState());

        // Idempotent replay: job is already in the target state — no mutation needed
        if (currentState == targetState) {
            log.debug("Idempotent replay: job '{}' already in state '{}'; no mutation applied",
                    jobId, targetState);
            return TransitionResult.builder()
                    .jobId(jobId)
                    .fromState(currentState)
                    .toState(targetState)
                    .correlationId(job.getCorrelationId())
                    .transitionedAt(job.getUpdatedAt())
                    .newVersion(job.getVersion())
                    .idempotentReplay(true)
                    .build();
        }

        // Validate before any mutation
        policy.validate(currentState, targetState);

        long expectedVersion = job.getVersion();
        try {
            repository.updateState(jobId, targetState.name(), expectedVersion);
        } catch (LifecyclePersistenceException e) {
            // updateState throws LifecyclePersistenceException on version mismatch
            throw new LifecycleConcurrencyException(jobId, expectedVersion);
        }

        log.info("Lifecycle transition: job='{}' cid='{}' {}→{} version={}→{}",
                jobId, job.getCorrelationId(), currentState, targetState,
                expectedVersion, expectedVersion + 1);

        // Optional checkpoint
        if (request.getCheckpointSequenceNumber() >= 0) {
            appendCheckpoint(job, targetState, request);
        }

        // Persist diagnostic for terminal failure states
        if (shouldPersistDiagnostic(targetState)) {
            persistDiagnostic(job, request);
        }

        return TransitionResult.builder()
                .jobId(jobId)
                .fromState(currentState)
                .toState(targetState)
                .correlationId(job.getCorrelationId())
                .transitionedAt(Instant.now())
                .newVersion(expectedVersion + 1)
                .idempotentReplay(false)
                .build();
    }

    /**
     * Convenience: transitions job to {@code ACCEPTED} state on initial creation.
     * The job record must already exist in the repository.
     */
    public TransitionResult accept(String jobId, String correlationId) {
        TransitionRequest request = TransitionRequest
                .builder(jobId, JobLifecycleState.ACCEPTED)
                .correlationId(correlationId)
                .checkpointSequenceNumber(0)
                .checkpointCode("JOB_ACCEPTED")
                .build();
        return transition(request);
    }

    /**
     * Convenience: transitions job to {@code DISPATCHING}.
     */
    public TransitionResult markDispatching(String jobId, String correlationId) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.DISPATCHING)
                .correlationId(correlationId)
                .checkpointCode("WORKER_DISPATCH_STARTED")
                .build());
    }

    /**
     * Convenience: transitions job to {@code RUNNING}.
     */
    public TransitionResult markRunning(String jobId, String correlationId, int checkpointSeq) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.RUNNING)
                .correlationId(correlationId)
                .checkpointSequenceNumber(checkpointSeq)
                .checkpointCode("WORKER_EXECUTION_STARTED")
                .build());
    }

    /**
     * Convenience: transitions job to {@code COMPLETED}.
     */
    public TransitionResult markCompleted(String jobId, String correlationId, int checkpointSeq) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.COMPLETED)
                .correlationId(correlationId)
                .checkpointSequenceNumber(checkpointSeq)
                .checkpointCode("EXECUTION_COMPLETED")
                .build());
    }

    /**
     * Convenience: transitions job to {@code FAILED} with an optional safe diagnostic.
     */
    public TransitionResult markFailed(String jobId, String correlationId,
                                       String safeReasonCode, String safeDiagnosticSummary) {
        String reasonCode = (safeReasonCode != null && !safeReasonCode.isBlank())
                ? safeReasonCode : DEFAULT_FAILURE_CODE;
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.FAILED)
                .correlationId(correlationId)
                .safeReasonCode(reasonCode)
                .safeDiagnosticSummary(safeDiagnosticSummary)
                .checkpointCode("EXECUTION_FAILED")
                .build());
    }

    /**
     * Convenience: transitions job to {@code CANCELLED}.
     */
    public TransitionResult markCancelled(String jobId, String correlationId, String safeReasonCode) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.CANCELLED)
                .correlationId(correlationId)
                .safeReasonCode(safeReasonCode)
                .checkpointCode("JOB_CANCELLED")
                .build());
    }

    /**
     * Convenience: transitions job to {@code TIMED_OUT}.
     */
    public TransitionResult markTimedOut(String jobId, String correlationId) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.TIMED_OUT)
                .correlationId(correlationId)
                .safeReasonCode("EXECUTION_TIMEOUT")
                .checkpointCode("JOB_TIMED_OUT")
                .build());
    }

    /**
     * Convenience: transitions job to {@code REJECTED}.
     */
    public TransitionResult markRejected(String jobId, String correlationId, String safeReasonCode) {
        return transition(TransitionRequest.builder(jobId, JobLifecycleState.REJECTED)
                .correlationId(correlationId)
                .safeReasonCode(safeReasonCode)
                .checkpointCode("JOB_REJECTED")
                .build());
    }

    // ---- private helpers ----

    private JobRecord loadJob(String jobId) {
        Optional<JobRecord> found = repository.findById(jobId);
        if (found.isEmpty()) {
            throw new LifecyclePersistenceException("Job not found: '" + jobId + "'");
        }
        return found.get();
    }

    private void appendCheckpoint(JobRecord job, JobLifecycleState newState, TransitionRequest req) {
        try {
            JobCheckpoint cp = new JobCheckpoint();
            cp.setJobId(job.getJobId());
            cp.setSequenceNumber(req.getCheckpointSequenceNumber());
            cp.setCheckpointCode(req.getCheckpointCode() != null ? req.getCheckpointCode() : newState.name());
            cp.setStateAtCheckpoint(newState.name());
            cp.setSafeMessage(req.getSafeReasonCode());
            cp.setCreatedAt(Instant.now());
            repository.saveCheckpoint(cp);
        } catch (Exception e) {
            log.warn("Failed to append checkpoint for job='{}': {} — state update was committed",
                    job.getJobId(), e.getMessage());
        }
    }

    private boolean shouldPersistDiagnostic(JobLifecycleState state) {
        return state == JobLifecycleState.FAILED
                || state == JobLifecycleState.TIMED_OUT
                || state == JobLifecycleState.REJECTED;
    }

    private void persistDiagnostic(JobRecord job, TransitionRequest req) {
        try {
            JobDiagnostic diag = new JobDiagnostic();
            diag.setJobId(job.getJobId());
            String summary = req.getSafeDiagnosticSummary();
            // Truncate to 1024 chars as per schema constraint; never log raw value
            if (summary != null && summary.length() > 1024) {
                summary = summary.substring(0, 1021) + "...";
            }
            diag.setSafeSummary(summary);
            diag.setErrorCode(req.getSafeReasonCode() != null ? req.getSafeReasonCode() : DEFAULT_FAILURE_CODE);
            diag.setCreatedAt(Instant.now());
            repository.saveDiagnostic(diag);
        } catch (Exception e) {
            log.warn("Failed to persist diagnostic for job='{}': {} — state update was committed",
                    job.getJobId(), e.getMessage());
        }
    }
}
