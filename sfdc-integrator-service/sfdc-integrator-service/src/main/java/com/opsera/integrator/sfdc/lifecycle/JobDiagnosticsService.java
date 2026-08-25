package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builds safe diagnostic summaries for stalled or failed release jobs.
 *
 * <p>Summaries are operator-readable but intentionally stripped of raw payloads,
 * stack traces, credentials, and CLI output. Only allow-listed structured fields
 * are included (failingStage, lastCheckpointCode, elapsedSeconds, operationType,
 * retryEligibilityHint, correlationId).
 *
 * <p>An injectable {@link Clock} enables deterministic unit testing without
 * real wall-clock dependencies.
 */
@Service
public class JobDiagnosticsService {

    private final Clock clock;

    public JobDiagnosticsService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Builds a safe diagnostic summary string for operator triage.
     *
     * <p>The summary is a structured text fragment (not JSON) containing only
     * allow-listed fields. It is safe to write to audit records and operational logs.
     *
     * @param job         the job record; must not be null
     * @param checkpoints ordered checkpoints for the job, oldest first; may be empty
     * @return a bounded safe summary string, never null
     */
    public String buildSafeSummary(JobRecord job, List<JobCheckpoint> checkpoints) {
        String lastCheckpointCode = resolveLastCheckpointCode(checkpoints);
        String failingStage = resolveFailingStage(checkpoints);
        long elapsedSeconds = resolveElapsedSeconds(job);
        String retryHint = resolveRetryEligibilityHint(job, checkpoints);

        StringBuilder sb = new StringBuilder();
        sb.append("operationType=").append(safe(job.getOperationType()));
        sb.append(" state=").append(safe(job.getCurrentState()));
        sb.append(" elapsedSeconds=").append(elapsedSeconds);
        sb.append(" lastCheckpointCode=").append(lastCheckpointCode);
        sb.append(" failingStage=").append(failingStage);
        sb.append(" retryEligibility=").append(retryHint);
        sb.append(" correlationId=").append(safe(job.getCorrelationId()));
        return sb.toString();
    }

    // ---- private helpers ----

    private String resolveLastCheckpointCode(List<JobCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return "NONE";
        }
        String code = checkpoints.get(checkpoints.size() - 1).getCheckpointCode();
        return code != null && !code.isBlank() ? code : "UNKNOWN";
    }

    private String resolveFailingStage(List<JobCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return "NONE";
        }
        for (int i = checkpoints.size() - 1; i >= 0; i--) {
            String code = checkpoints.get(i).getCheckpointCode();
            if (code != null && (code.contains("FAIL") || code.contains("ERROR")
                    || code.contains("REJECT") || code.contains("TIMEOUT"))) {
                return code;
            }
        }
        return "NONE";
    }

    private long resolveElapsedSeconds(JobRecord job) {
        Instant created = job.getCreatedAt();
        if (created == null) {
            return -1L;
        }
        Duration elapsed = Duration.between(created, Instant.now(clock));
        return Math.max(0L, elapsed.getSeconds());
    }

    private String resolveRetryEligibilityHint(JobRecord job, List<JobCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return "UNKNOWN";
        }
        String lastCode = checkpoints.get(checkpoints.size() - 1).getCheckpointCode();
        if (lastCode != null && lastCode.contains("REJECT")) {
            return "NOT_ELIGIBLE";
        }
        String operationType = job.getOperationType();
        if ("QUICK_DEPLOY".equals(operationType) || "DEPLOY".equals(operationType)) {
            return "ELIGIBLE_IF_IDEMPOTENT";
        }
        return "UNKNOWN";
    }

    private static String safe(String value) {
        return (value != null && !value.isBlank()) ? value : "UNKNOWN";
    }
}
