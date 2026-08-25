package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecycleConcurrencyException;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.lifecycle.LifecycleTransitionException;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates durable persistence of a Kafka checkpoint event.
 *
 * <p>Processing flow:
 * <ol>
 *   <li>Map the event to a {@link CheckpointMappingResult} via {@link CheckpointMapper}.</li>
 *   <li>Handle MALFORMED, ORPHANED, and DUPLICATE outcomes with safe structured logging.</li>
 *   <li>Verify the referenced job exists in the repository (ORPHANED if missing).</li>
 *   <li>Check for duplicate event key in existing checkpoints (DUPLICATE if found).</li>
 *   <li>Persist the checkpoint record via {@link JobRepository}.</li>
 *   <li>Apply the optional state transition via {@link JobLifecycleService}.</li>
 * </ol>
 *
 * <p>State transitions that violate the lifecycle policy (e.g. overwriting a terminal state)
 * are caught, logged with safe metadata, and skipped — the checkpoint record is still persisted.
 *
 * <p>All log messages are allow-listed: only jobId, correlationId, checkpointCode, eventKey,
 * and outcome codes are emitted. Raw message payloads are never logged.
 */
@Service
public class CheckpointPersistenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(CheckpointPersistenceAdapter.class);

    private final CheckpointMapper mapper;
    private final JobRepository jobRepository;
    private final JobLifecycleService lifecycleService;

    public CheckpointPersistenceAdapter(CheckpointMapper mapper,
                                        JobRepository jobRepository,
                                        JobLifecycleService lifecycleService) {
        this.mapper = mapper;
        this.jobRepository = jobRepository;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Processes a single checkpoint event: maps, validates, persists, and optionally
     * transitions job state.
     *
     * @param event the incoming checkpoint event; may be null (handled as malformed)
     * @return a {@link ProcessingOutcome} describing what happened
     */
    public ProcessingOutcome process(CheckpointEvent event) {
        // Step 1: Map
        CheckpointMappingResult result = mapper.map(event);

        String jobId = event != null ? event.getJobId() : "null";
        String code = event != null ? event.getCheckpointCode() : "null";

        // Step 2: Handle non-persist outcomes
        switch (result.getOutcome()) {
            case MALFORMED:
                log.warn("checkpoint-malformed: jobId='{}' code='{}' reason='{}'",
                        jobId, code, result.getSafeSkipReason());
                return ProcessingOutcome.MALFORMED;

            case DUPLICATE:
                log.info("checkpoint-duplicate: eventKey='{}' — skipped",
                        result.getEventKey());
                return ProcessingOutcome.DUPLICATE;

            case ORPHANED:
                log.warn("checkpoint-orphaned: jobId='{}' code='{}' eventKey='{}' — job not found",
                        jobId, code, result.getEventKey());
                return ProcessingOutcome.ORPHANED;

            case PERSIST:
                break;
        }

        JobCheckpoint checkpoint = result.getCheckpoint();

        // Step 3: Verify job exists
        Optional<JobRecord> jobOpt = jobRepository.findById(checkpoint.getJobId());
        if (jobOpt.isEmpty()) {
            log.warn("checkpoint-orphaned: jobId='{}' code='{}' eventKey='{}' — job not found in repository",
                    checkpoint.getJobId(), checkpoint.getCheckpointCode(), result.getEventKey());
            return ProcessingOutcome.ORPHANED;
        }

        // Step 4: Duplicate detection using (jobId, sequenceNumber) key.
        // The database enforces uniqueness on (jobId, sequenceNumber); a duplicate will throw
        // LifecyclePersistenceException on saveCheckpoint. We pre-check to give a cleaner log.
        if (isDuplicate(checkpoint.getJobId(), checkpoint.getSequenceNumber())) {
            log.info("checkpoint-duplicate: jobId='{}' seq={} eventKey='{}' — already persisted",
                    checkpoint.getJobId(), checkpoint.getSequenceNumber(), result.getEventKey());
            return ProcessingOutcome.DUPLICATE;
        }

        // Step 5: Persist checkpoint record
        try {
            jobRepository.saveCheckpoint(checkpoint);
        } catch (LifecyclePersistenceException e) {
            if (isDuplicateConstraintViolation(e)) {
                log.info("checkpoint-duplicate (constraint): jobId='{}' seq={} — already persisted",
                        checkpoint.getJobId(), checkpoint.getSequenceNumber());
                return ProcessingOutcome.DUPLICATE;
            }
            log.warn("checkpoint-persist-failed: jobId='{}' code='{}' error='{}'",
                    checkpoint.getJobId(), checkpoint.getCheckpointCode(), e.getMessage());
            return ProcessingOutcome.PERSISTENCE_ERROR;
        }

        // Step 6: Apply state transition if present
        if (result.hasStateTransition()) {
            applyStateTransition(jobOpt.get(), result.getStateTransition(),
                    event.getCorrelationId(), checkpoint.getSequenceNumber());
        }

        log.info("checkpoint-persisted: jobId='{}' code='{}' seq={} eventKey='{}'",
                checkpoint.getJobId(), checkpoint.getCheckpointCode(),
                checkpoint.getSequenceNumber(), result.getEventKey());

        return ProcessingOutcome.PERSISTED;
    }

    private void applyStateTransition(JobRecord job,
                                      CheckpointStateTransition transition,
                                      String correlationId,
                                      int checkpointSeq) {
        String jobId = job.getJobId();
        String cid = (correlationId != null && !correlationId.isBlank())
                ? correlationId : job.getCorrelationId();
        JobLifecycleState target = transition.getTargetState();

        try {
            switch (target) {
                case RUNNING:
                    lifecycleService.markRunning(jobId, cid, checkpointSeq);
                    break;
                case COMPLETED:
                    lifecycleService.markCompleted(jobId, cid, checkpointSeq);
                    break;
                case FAILED:
                    lifecycleService.markFailed(jobId, cid,
                            transition.getSafeReasonCode(),
                            transition.getSafeDiagnosticSummary());
                    break;
                case CANCELLED:
                    lifecycleService.markCancelled(jobId, cid, transition.getSafeReasonCode());
                    break;
                case TIMED_OUT:
                    lifecycleService.markTimedOut(jobId, cid);
                    break;
                default:
                    log.debug("No lifecycle transition handler for target state '{}' — checkpoint only",
                            target);
            }
            log.info("lifecycle-transition: jobId='{}' cid='{}' targetState='{}'",
                    jobId, cid, target);
        } catch (LifecycleTransitionException e) {
            // Terminal state reached or invalid transition — log and skip (AC edge case)
            log.warn("lifecycle-transition-skipped: jobId='{}' target='{}' reason='{}'",
                    jobId, target, e.getMessage());
        } catch (LifecycleConcurrencyException e) {
            log.warn("lifecycle-transition-concurrent: jobId='{}' target='{}' — concurrent modification",
                    jobId, target);
        } catch (LifecyclePersistenceException e) {
            log.warn("lifecycle-transition-persistence-failed: jobId='{}' target='{}' error='{}'",
                    jobId, target, e.getMessage());
        }
    }

    private boolean isDuplicate(String jobId, int sequenceNumber) {
        try {
            var checkpoints = jobRepository.findCheckpointsByJobId(jobId);
            return checkpoints.stream()
                    .anyMatch(cp -> cp.getSequenceNumber() == sequenceNumber);
        } catch (Exception e) {
            log.debug("Could not pre-check duplicates for jobId='{}' seq={}: {}",
                    jobId, sequenceNumber, e.getMessage());
            return false;
        }
    }

    private boolean isDuplicateConstraintViolation(LifecyclePersistenceException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("unique") || msg.contains("duplicate")
                || msg.contains("already exists") || msg.contains("constraint"));
    }

    /**
     * Outcomes for a single checkpoint event processing attempt.
     */
    public enum ProcessingOutcome {
        /** Checkpoint was persisted and lifecycle state updated if applicable. */
        PERSISTED,
        /** Duplicate event detected — no duplicate record created. */
        DUPLICATE,
        /** Job not found — event quarantined as orphaned. */
        ORPHANED,
        /** Event was missing required fields or otherwise malformed. */
        MALFORMED,
        /** Repository save failed for a non-duplicate reason. */
        PERSISTENCE_ERROR
    }
}
