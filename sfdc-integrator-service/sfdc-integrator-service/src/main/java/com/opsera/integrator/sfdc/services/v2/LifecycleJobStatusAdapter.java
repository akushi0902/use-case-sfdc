package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecycleTransitionException;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.resources.v2.release.CheckpointSummary;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * V2 status adapter backed by the durable lifecycle job repository (WO-119).
 *
 * <p>This is the current (and intended long-term) implementation of {@link JobStatusAdapter}.
 * It reads {@link JobRecord} and {@link JobCheckpoint} records from {@link JobRepository},
 * maps them to the canonical v2 status DTO, and applies safe-output guarantees:
 *
 * <ul>
 *   <li>Checkpoints are returned sorted by {@code sequenceNumber} ascending regardless
 *       of insertion order.</li>
 *   <li>Diagnostic summaries are truncated at {@link #MAX_DIAGNOSTIC_LENGTH} characters.
 *       No raw stack traces, credentials, Salesforce payloads, or CLI output are included.</li>
 *   <li>State is mapped from the internal {@link JobLifecycleState} to the public
 *       {@link ReleaseLifecycleState}; unknown internal states fall back to ACCEPTED.</li>
 *   <li>{@code statusSource} is set to {@value #STATUS_SOURCE_LABEL} so callers and operators
 *       can identify the data origin and understand the adapter is replaceable.</li>
 * </ul>
 *
 * <p>To replace this adapter with a different status source (e.g. a Hazelcast cache or a
 * read replica), implement {@link JobStatusAdapter} and register the new bean. The controller
 * and exception handler are unaffected.
 */
@Service
public class LifecycleJobStatusAdapter implements JobStatusAdapter {

    private static final Logger log = LoggerFactory.getLogger(LifecycleJobStatusAdapter.class);

    static final String STATUS_SOURCE_LABEL = "DURABLE_LIFECYCLE";
    static final int MAX_DIAGNOSTIC_LENGTH = 512;

    private final JobRepository jobRepository;

    public LifecycleJobStatusAdapter(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Returns the v2 status response for the given job ID.
     *
     * @param jobId validated (non-blank, safe) job identifier
     * @return populated response, or empty if the job is not found
     */
    @Override
    public Optional<ReleaseJobStatusResponse> findByJobId(String jobId) {
        Optional<JobRecord> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        JobRecord job = jobOpt.get();
        List<JobCheckpoint> checkpoints = fetchCheckpointsSafe(jobId);

        ReleaseJobStatusResponse response = new ReleaseJobStatusResponse();
        response.setJobId(job.getJobId());
        response.setCorrelationId(job.getCorrelationId());
        response.setOperationType(job.getOperationType());
        response.setState(mapState(job.getCurrentState()));
        response.setAcceptedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        response.setCheckpoints(mapCheckpoints(checkpoints));
        response.setSafeDiagnosticSummary(buildDiagnosticSummary(job, checkpoints));
        response.setStatusSource(STATUS_SOURCE_LABEL);

        return Optional.of(response);
    }

    /**
     * Maps the internal {@link JobLifecycleState} string to the public {@link ReleaseLifecycleState}.
     *
     * <p>REJECTED (an internal-only terminal state for policy violations) is mapped to FAILED
     * since it is not part of the public v2 state vocabulary.
     * Unknown state strings fall back to ACCEPTED to avoid exposing internal error details.
     */
    ReleaseLifecycleState mapState(String stateString) {
        if (stateString == null || stateString.isBlank()) {
            return ReleaseLifecycleState.ACCEPTED;
        }
        try {
            JobLifecycleState jls = JobLifecycleState.fromString(stateString);
            switch (jls) {
                case ACCEPTED:     return ReleaseLifecycleState.ACCEPTED;
                case DISPATCHING:  return ReleaseLifecycleState.DISPATCHING;
                case RUNNING:      return ReleaseLifecycleState.RUNNING;
                case COMPLETED:    return ReleaseLifecycleState.COMPLETED;
                case FAILED:       return ReleaseLifecycleState.FAILED;
                case CANCELLED:    return ReleaseLifecycleState.CANCELLED;
                case TIMED_OUT:    return ReleaseLifecycleState.TIMED_OUT;
                case REJECTED:     return ReleaseLifecycleState.FAILED;
                default:           return ReleaseLifecycleState.ACCEPTED;
            }
        } catch (LifecycleTransitionException e) {
            log.warn("Unknown lifecycle state string '{}' — defaulting to ACCEPTED", stateString);
            return ReleaseLifecycleState.ACCEPTED;
        }
    }

    /**
     * Maps persisted {@link JobCheckpoint} records to safe {@link CheckpointSummary} DTOs,
     * sorted by sequence number ascending for deterministic ordering regardless of arrival order.
     */
    private List<CheckpointSummary> mapCheckpoints(List<JobCheckpoint> checkpoints) {
        return checkpoints.stream()
                .sorted(Comparator.comparingInt(JobCheckpoint::getSequenceNumber))
                .map(this::toCheckpointSummary)
                .collect(Collectors.toList());
    }

    private CheckpointSummary toCheckpointSummary(JobCheckpoint cp) {
        CheckpointSummary summary = new CheckpointSummary();
        summary.setSequenceNumber(cp.getSequenceNumber());
        summary.setCheckpointCode(cp.getCheckpointCode());
        summary.setStateAtCheckpoint(cp.getStateAtCheckpoint());
        summary.setSafeMessage(truncateSafe(cp.getSafeMessage(), MAX_DIAGNOSTIC_LENGTH));
        summary.setRecordedAt(cp.getCreatedAt());
        return summary;
    }

    /**
     * Builds a safe diagnostic summary for terminal failed/cancelled states.
     * Returns null for non-terminal or no-diagnostic states.
     * Never includes raw error messages, stack traces, or credential fragments.
     */
    private String buildDiagnosticSummary(JobRecord job, List<JobCheckpoint> checkpoints) {
        ReleaseLifecycleState state = mapState(job.getCurrentState());
        if (state != ReleaseLifecycleState.FAILED
                && state != ReleaseLifecycleState.CANCELLED
                && state != ReleaseLifecycleState.TIMED_OUT) {
            return null;
        }
        // Summarize using the last checkpoint message as the safe diagnostic
        if (!checkpoints.isEmpty()) {
            JobCheckpoint last = checkpoints.stream()
                    .max(Comparator.comparingInt(JobCheckpoint::getSequenceNumber))
                    .orElse(null);
            if (last != null && last.getSafeMessage() != null) {
                return truncateSafe(last.getSafeMessage(), MAX_DIAGNOSTIC_LENGTH);
            }
        }
        return state.name() + " — no diagnostic detail available";
    }

    /**
     * Fetches checkpoints for the given job, logging a safe warning on failure
     * rather than propagating the error (checkpoints are supplementary data).
     */
    private List<JobCheckpoint> fetchCheckpointsSafe(String jobId) {
        try {
            return jobRepository.findCheckpointsByJobId(jobId);
        } catch (Exception e) {
            log.warn("Could not fetch checkpoints for jobId='{}' — returning empty list: {}",
                    jobId, e.getMessage());
            return List.of();
        }
    }

    private String truncateSafe(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
