package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Converts incoming {@link CheckpointEvent} payloads to {@link CheckpointMappingResult} objects.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>A valid event must have a non-blank {@code jobId} and {@code checkpointCode}.</li>
 *   <li>Known status categories ({@code RUNNING}, {@code COMPLETED}, {@code FAILED},
 *       {@code CANCELLED}, {@code TIMED_OUT}) produce a state transition request.</li>
 *   <li>{@code PROGRESS} category events produce a checkpoint record only — no state transition.</li>
 *   <li>Unknown or missing categories are mapped to checkpoint-only with a safe log warning.</li>
 *   <li>A deterministic event key is computed from jobId, checkpointCode, and sequenceNumber
 *       so duplicate detection can be performed by the adapter.</li>
 * </ul>
 *
 * <p>Safe fields only: {@code safeMessage} is truncated to 512 characters. Unsafe patterns
 * (potential credential tokens, Salesforce Org IDs) in safeMessage are replaced with a
 * redacted marker rather than being persisted.
 *
 * <p>This class has no Spring dependencies and can be instantiated directly in unit tests.
 */
@Component
public class CheckpointMapper {

    private static final Logger log = LoggerFactory.getLogger(CheckpointMapper.class);

    static final int MAX_SAFE_MESSAGE_LENGTH = 512;
    private static final String REDACTED = "[REDACTED]";

    /** Status categories that imply a lifecycle state transition. */
    private static final Set<String> TRANSITION_CATEGORIES =
            Set.of("RUNNING", "COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT");

    /**
     * Maps a {@link CheckpointEvent} to a {@link CheckpointMappingResult}.
     *
     * @param event the incoming event; may be null (returns MALFORMED)
     * @return mapping result; never null
     */
    public CheckpointMappingResult map(CheckpointEvent event) {
        if (event == null) {
            return CheckpointMappingResult.builder(CheckpointMappingResult.MappingOutcome.MALFORMED)
                    .safeSkipReason("null checkpoint event")
                    .build();
        }

        // Validate required fields
        if (event.getJobId() == null || event.getJobId().isBlank()) {
            return CheckpointMappingResult.builder(CheckpointMappingResult.MappingOutcome.MALFORMED)
                    .safeSkipReason("missing jobId")
                    .build();
        }
        if (event.getCheckpointCode() == null || event.getCheckpointCode().isBlank()) {
            return CheckpointMappingResult.builder(CheckpointMappingResult.MappingOutcome.MALFORMED)
                    .eventKey(buildEventKey(event))
                    .safeSkipReason("missing checkpointCode")
                    .build();
        }

        String eventKey = buildEventKey(event);
        JobCheckpoint checkpoint = buildCheckpoint(event);
        CheckpointStateTransition transition = resolveTransition(event);

        if (transition == null) {
            log.debug("Checkpoint mapped as progress-only: jobId='{}' code='{}' category='{}'",
                    event.getJobId(), event.getCheckpointCode(), event.getStatusCategory());
        }

        return CheckpointMappingResult.builder(CheckpointMappingResult.MappingOutcome.PERSIST)
                .checkpoint(checkpoint)
                .stateTransition(transition)
                .eventKey(eventKey)
                .build();
    }

    /**
     * Builds a deterministic event key for duplicate detection.
     * Key is derived from safe, non-sensitive fields only.
     */
    String buildEventKey(CheckpointEvent event) {
        if (event == null) return "null-event";
        String jobId = event.getJobId() != null ? event.getJobId() : "no-job";
        String code = event.getCheckpointCode() != null ? event.getCheckpointCode() : "no-code";
        return jobId + "|" + code + "|" + event.getSequenceNumber();
    }

    private JobCheckpoint buildCheckpoint(CheckpointEvent event) {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setJobId(event.getJobId());
        cp.setSequenceNumber(event.getSequenceNumber());
        cp.setCheckpointCode(event.getCheckpointCode());
        cp.setStateAtCheckpoint(resolveStateAtCheckpoint(event));
        cp.setSafeMessage(sanitizeSafeMessage(event.getSafeMessage()));
        cp.setCreatedAt(parseEventTimestamp(event.getEventTimestamp()));
        return cp;
    }

    private String resolveStateAtCheckpoint(CheckpointEvent event) {
        String category = event.getStatusCategory();
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        String upper = category.trim().toUpperCase();
        // Map statusCategory to a known lifecycle state name
        switch (upper) {
            case "RUNNING":   return "RUNNING";
            case "COMPLETED": return "COMPLETED";
            case "FAILED":    return "FAILED";
            case "CANCELLED": return "CANCELLED";
            case "TIMED_OUT": return "TIMED_OUT";
            case "PROGRESS":  return "RUNNING";
            default:          return "UNKNOWN";
        }
    }

    private CheckpointStateTransition resolveTransition(CheckpointEvent event) {
        String category = event.getStatusCategory();
        if (category == null || category.isBlank()) {
            return null;
        }
        String upper = category.trim().toUpperCase();
        if (!TRANSITION_CATEGORIES.contains(upper)) {
            return null;
        }
        switch (upper) {
            case "RUNNING":
                return new CheckpointStateTransition(JobLifecycleState.RUNNING, null, null);
            case "COMPLETED":
                return new CheckpointStateTransition(JobLifecycleState.COMPLETED, null, null);
            case "FAILED":
                return new CheckpointStateTransition(
                        JobLifecycleState.FAILED,
                        event.getSafeReasonCode() != null ? event.getSafeReasonCode() : "EXECUTION_FAILED",
                        sanitizeSafeMessage(event.getSafeMessage()));
            case "CANCELLED":
                return new CheckpointStateTransition(
                        JobLifecycleState.CANCELLED,
                        event.getSafeReasonCode() != null ? event.getSafeReasonCode() : "CANCELLED_BY_EVENT",
                        null);
            case "TIMED_OUT":
                return new CheckpointStateTransition(
                        JobLifecycleState.TIMED_OUT,
                        "EXECUTION_TIMEOUT",
                        null);
            default:
                return null;
        }
    }

    private Instant parseEventTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            log.debug("Could not parse eventTimestamp '{}' — using current time", raw);
            return Instant.now();
        }
    }

    /**
     * Sanitizes a safeMessage to prevent persistence of potentially sensitive content.
     * Truncates to MAX_SAFE_MESSAGE_LENGTH. Replaces content that looks like credentials
     * (long hex strings, bearer tokens) with a redacted marker.
     */
    String sanitizeSafeMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String truncated = raw.length() > MAX_SAFE_MESSAGE_LENGTH
                ? raw.substring(0, MAX_SAFE_MESSAGE_LENGTH - 3) + "..."
                : raw;
        // Replace 40+ char hex sequences (potential SHA/token fragments)
        truncated = truncated.replaceAll("[0-9a-fA-F]{40,}", REDACTED);
        // Replace "Bearer <token>" patterns
        truncated = truncated.replaceAll("(?i)bearer\\s+[\\w.-]+", "Bearer " + REDACTED);
        return truncated;
    }
}
