package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link CommandDispatcher} implementation.
 *
 * <p>Dispatch sequence:
 * <ol>
 *   <li>Resolve or generate a correlation ID; propagate into MDC.</li>
 *   <li>Check for an existing job matching the idempotency key — return the original
 *       outcome without re-creating the job (idempotent replay).</li>
 *   <li>Create and persist a {@link JobRecord} directly in {@code ACCEPTED} state.</li>
 *   <li>Save an initial acceptance checkpoint for traceability.</li>
 *   <li>Hand off to the {@link ReleaseCommandAdapter} — if this fails, transition to
 *       {@code FAILED} and throw {@link CommandDispatchException}.</li>
 *   <li>Return an {@link AcceptedCommandOutcome} with job ID, correlation ID, status
 *       path, state, and accepted timestamp.</li>
 * </ol>
 *
 * <p>Job records are saved directly in {@code ACCEPTED} state (rather than via a lifecycle
 * transition) because there is no valid predecessor state in the lifecycle policy matrix —
 * acceptance is the initial state. Subsequent transitions (DISPATCHING, RUNNING, FAILED, etc.)
 * are performed through {@link JobLifecycleService} as normal.
 *
 * <p>Lifecycle record creation failure (step 3) prevents the adapter from being invoked.
 * All log messages use only safe operational fields; credentials and raw payloads are never logged.
 */
@Service
public class DefaultCommandDispatcher implements CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultCommandDispatcher.class);
    static final String STATUS_PATH_TEMPLATE = "/api/v2/sfdc/release-jobs/%s/status";
    static final String MDC_CORRELATION_KEY = "correlationId";

    private final JobRepository jobRepository;
    private final JobLifecycleService lifecycleService;
    private final ReleaseCommandAdapter adapter;

    public DefaultCommandDispatcher(JobRepository jobRepository,
                                    JobLifecycleService lifecycleService,
                                    ReleaseCommandAdapter adapter) {
        this.jobRepository = jobRepository;
        this.lifecycleService = lifecycleService;
        this.adapter = adapter;
    }

    @Override
    public AcceptedCommandOutcome dispatch(AsyncCommandRequest command) {
        if (command == null) {
            throw new IllegalArgumentException("AsyncCommandRequest must not be null");
        }

        String correlationId = resolveCorrelationId(command.getCorrelationId());
        String previousMdcValue = MDC.get(MDC_CORRELATION_KEY);
        MDC.put(MDC_CORRELATION_KEY, correlationId);

        try {
            // Idempotency check — if caller provided an idempotency key and a matching job
            // already exists, return the original outcome without creating a duplicate.
            if (command.getIdempotencyKey() != null && !command.getIdempotencyKey().isBlank()) {
                Optional<JobRecord> existing = jobRepository.findByCorrelationId(command.getIdempotencyKey());
                if (existing.isPresent()) {
                    JobRecord existingJob = existing.get();
                    log.info("Idempotent replay: jobId='{}' correlationId='{}' operationType='{}'",
                            existingJob.getJobId(), correlationId, command.getOperationType());
                    return buildOutcome(existingJob.getJobId(), correlationId,
                            command.getOperationType(), existingJob.getCurrentState(),
                            existingJob.getCreatedAt(), true);
                }
            }

            String jobId = generateJobId();
            Instant now = Instant.now();

            // Step 1: Persist job record in ACCEPTED state.
            // Jobs are created directly in ACCEPTED state — there is no valid predecessor
            // state in the lifecycle policy matrix. Subsequent lifecycle transitions
            // (DISPATCHING, RUNNING, FAILED, etc.) go through JobLifecycleService.
            JobRecord jobRecord = buildJobRecord(jobId, correlationId, command, now);
            try {
                jobRepository.save(jobRecord);
            } catch (LifecyclePersistenceException e) {
                log.error("Failed to persist job record: jobId='{}' correlationId='{}': {}",
                        jobId, correlationId, e.getMessage());
                throw e;
            }

            log.info("Job accepted: jobId='{}' correlationId='{}' operationType='{}'",
                    jobId, correlationId, command.getOperationType());

            // Step 2: Save initial acceptance checkpoint for audit/traceability.
            saveAcceptanceCheckpoint(jobId, now);

            // Step 3: Hand off to adapter. Failure marks the job FAILED.
            try {
                adapter.execute(command);
            } catch (Exception adapterEx) {
                log.warn("Adapter handoff failed for jobId='{}' correlationId='{}': {}",
                        jobId, correlationId, adapterEx.getMessage());
                safeMarkFailed(jobId, correlationId, "ADAPTER_HANDOFF_FAILURE");
                throw new CommandDispatchException(jobId, correlationId,
                        "ADAPTER_HANDOFF_FAILURE",
                        "Adapter handoff failed for job '" + jobId + "'",
                        adapterEx);
            }

            log.info("Command dispatched: jobId='{}' correlationId='{}' operationType='{}' state='ACCEPTED'",
                    jobId, correlationId, command.getOperationType());

            return buildOutcome(jobId, correlationId, command.getOperationType(),
                    JobLifecycleState.ACCEPTED.name(), now, false);

        } finally {
            if (previousMdcValue != null) {
                MDC.put(MDC_CORRELATION_KEY, previousMdcValue);
            } else {
                MDC.remove(MDC_CORRELATION_KEY);
            }
        }
    }

    private String resolveCorrelationId(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return "cid-" + UUID.randomUUID();
    }

    private String generateJobId() {
        return "job-" + UUID.randomUUID();
    }

    private JobRecord buildJobRecord(String jobId, String correlationId,
                                     AsyncCommandRequest command, Instant now) {
        JobRecord record = new JobRecord();
        record.setJobId(jobId);
        record.setCorrelationId(correlationId);
        record.setOperationType(command.getOperationType());
        record.setCurrentState(JobLifecycleState.ACCEPTED.name());
        record.setCustomerIdHash(command.getCustomerIdHash());
        record.setSfdcToolId(command.getSfdcToolId());
        record.setPipelineId(command.getPipelineId());
        record.setStepId(command.getStepId());
        record.setDataClassification(command.getDataClassification() != null
                ? command.getDataClassification() : "CONFIDENTIAL");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setVersion(0L);
        return record;
    }

    private void saveAcceptanceCheckpoint(String jobId, Instant now) {
        try {
            JobCheckpoint checkpoint = new JobCheckpoint();
            checkpoint.setJobId(jobId);
            checkpoint.setSequenceNumber(0);
            checkpoint.setCheckpointCode("JOB_ACCEPTED");
            checkpoint.setStateAtCheckpoint(JobLifecycleState.ACCEPTED.name());
            checkpoint.setCreatedAt(now);
            jobRepository.saveCheckpoint(checkpoint);
        } catch (Exception e) {
            log.warn("Failed to save acceptance checkpoint for jobId='{}': {} — job was accepted",
                    jobId, e.getMessage());
        }
    }

    private AcceptedCommandOutcome buildOutcome(String jobId, String correlationId,
                                                String operationType, String state,
                                                Instant acceptedAt, boolean idempotentReplay) {
        String statusPath = String.format(STATUS_PATH_TEMPLATE, jobId);
        return AcceptedCommandOutcome.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .statusPath(statusPath)
                .state(state)
                .acceptedAt(acceptedAt)
                .operationType(operationType)
                .idempotentReplay(idempotentReplay)
                .build();
    }

    private void safeMarkFailed(String jobId, String correlationId, String reasonCode) {
        try {
            lifecycleService.markFailed(jobId, correlationId, reasonCode,
                    "Adapter handoff failed during dispatch");
        } catch (Exception e) {
            log.warn("Could not mark job failed after adapter error: jobId='{}' error='{}'",
                    jobId, e.getMessage());
        }
    }
}
