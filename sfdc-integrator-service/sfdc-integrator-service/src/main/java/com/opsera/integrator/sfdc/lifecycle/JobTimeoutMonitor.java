package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled monitor that detects stalled active jobs and finalizes them as
 * {@code TIMED_OUT} through the lifecycle service.
 *
 * <p>Design invariants:
 * <ul>
 *   <li>Transitions only go through {@link JobLifecycleService} — no direct DB updates.</li>
 *   <li>Terminal jobs are never overwritten: {@link LifecycleTransitionException} from
 *       the policy is caught and counted as a skip, not an error.</li>
 *   <li>Optimistic concurrency conflicts ({@link LifecycleConcurrencyException}) are treated
 *       as safe skips — a concurrent replica already handled that job.</li>
 *   <li>Per-job errors are isolated so one malformed record does not abort the scan.</li>
 *   <li>ACCEPTED jobs with no checkpoints must survive the grace period before timeout.</li>
 *   <li>Metrics tags are bounded: no jobId, correlationId, or customer identifier.</li>
 * </ul>
 *
 * <p>Disable during emergency maintenance by setting
 * {@code sfdc.lifecycle.timeout.enabled=false}.
 */
@Component
public class JobTimeoutMonitor {

    private static final Logger log = LoggerFactory.getLogger(JobTimeoutMonitor.class);

    static final String METRIC_SCAN_TOTAL   = "sfdc.lifecycle.timeout.scan.total";
    static final String METRIC_FINALIZED    = "sfdc.lifecycle.timeout.finalized";
    static final String METRIC_SKIPPED      = "sfdc.lifecycle.timeout.skipped";
    static final String METRIC_ERRORS       = "sfdc.lifecycle.timeout.errors";

    private final JobTimeoutProperties properties;
    private final JobRepository jobRepository;
    private final JobLifecycleService lifecycleService;
    private final JobDiagnosticsService diagnosticsService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public JobTimeoutMonitor(JobTimeoutProperties properties,
                             JobRepository jobRepository,
                             JobLifecycleService lifecycleService,
                             JobDiagnosticsService diagnosticsService,
                             MeterRegistry meterRegistry,
                             Clock clock) {
        this.properties = properties;
        this.jobRepository = jobRepository;
        this.lifecycleService = lifecycleService;
        this.diagnosticsService = diagnosticsService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Scheduled scan: finds stale active jobs and finalizes them as timed out.
     *
     * <p>The {@code fixedDelay} ensures consecutive scans do not overlap — the next
     * scan begins only after the previous one completes, protecting against scan pile-up.
     */
    @Scheduled(fixedDelayString = "#{${sfdc.lifecycle.timeout.scan-interval-seconds:300} * 1000}")
    public void runScan() {
        if (!properties.isEnabled()) {
            log.debug("timeout-monitor disabled — skipping scan");
            return;
        }

        log.info("timeout-monitor scan-start maxJobs={} staleThresholdMinutes={}",
                properties.getMaxJobsPerScan(), properties.getStaleThresholdMinutes());

        Instant staleBefore = Instant.now(clock)
                .minus(properties.getStaleThresholdMinutes(), ChronoUnit.MINUTES);
        Instant acceptedGraceExpiry = Instant.now(clock)
                .minus(properties.getAcceptedGracePeriodMinutes(), ChronoUnit.MINUTES);

        List<JobRecord> candidates;
        try {
            candidates = jobRepository.findStaleActiveJobs(staleBefore, properties.getMaxJobsPerScan());
        } catch (Exception e) {
            log.error("timeout-monitor scan-query-failed: {}", e.getMessage());
            incrementCounter(METRIC_ERRORS, "reason", "query_failed");
            return;
        }

        log.info("timeout-monitor scan-candidates count={}", candidates.size());
        incrementCounter(METRIC_SCAN_TOTAL, "result", "ok");

        int finalized = 0;
        int skipped = 0;
        int errors = 0;

        for (JobRecord job : candidates) {
            try {
                if (shouldSkipForGracePeriod(job, acceptedGraceExpiry)) {
                    log.debug("timeout-monitor grace-skip jobId={} state={}",
                            job.getJobId(), job.getCurrentState());
                    skipped++;
                    incrementCounter(METRIC_SKIPPED, "reason", "grace_period");
                    continue;
                }

                List<JobCheckpoint> checkpoints = jobRepository.findCheckpointsByJobId(job.getJobId());
                String summary = diagnosticsService.buildSafeSummary(job, checkpoints);
                String correlationId = job.getCorrelationId() != null ? job.getCorrelationId() : "unknown";

                lifecycleService.markTimedOut(job.getJobId(), correlationId, summary);
                log.info("timeout-monitor finalized jobId={} state={}→TIMED_OUT",
                        job.getJobId(), job.getCurrentState());
                finalized++;
                incrementCounter(METRIC_FINALIZED, "operationType",
                        safeOperationType(job.getOperationType()));

            } catch (LifecycleConcurrencyException e) {
                log.debug("timeout-monitor concurrency-skip jobId={} — already handled by another replica",
                        job.getJobId());
                skipped++;
                incrementCounter(METRIC_SKIPPED, "reason", "concurrency_conflict");
            } catch (LifecycleTransitionException e) {
                log.debug("timeout-monitor terminal-skip jobId={} — already in terminal state",
                        job.getJobId());
                skipped++;
                incrementCounter(METRIC_SKIPPED, "reason", "already_terminal");
            } catch (Exception e) {
                log.error("timeout-monitor per-job-error jobId={} error={}",
                        job.getJobId(), e.getMessage());
                errors++;
                incrementCounter(METRIC_ERRORS, "reason", "per_job_error");
            }
        }

        log.info("timeout-monitor scan-complete finalized={} skipped={} errors={}",
                finalized, skipped, errors);
    }

    // ---- private helpers ----

    private boolean shouldSkipForGracePeriod(JobRecord job, Instant acceptedGraceExpiry) {
        if (!"ACCEPTED".equals(job.getCurrentState())) {
            return false;
        }
        Instant createdAt = job.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return createdAt.isAfter(acceptedGraceExpiry);
    }

    private void incrementCounter(String metricName, String tagKey, String tagValue) {
        try {
            Counter.builder(metricName)
                    .tag(tagKey, tagValue)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("timeout-monitor metric-emit-failure metric={}", metricName);
        }
    }

    private static String safeOperationType(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        switch (raw.trim().toUpperCase()) {
            case "QUICK_DEPLOY": return "QUICK_DEPLOY";
            case "DEPLOY":       return "DEPLOY";
            case "VALIDATE":     return "VALIDATE";
            case "CANCEL":       return "CANCEL";
            default:             return "UNKNOWN";
        }
    }
}
