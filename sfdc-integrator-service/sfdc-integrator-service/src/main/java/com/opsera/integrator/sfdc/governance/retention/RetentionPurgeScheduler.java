package com.opsera.integrator.sfdc.governance.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled wrapper for the automated retention purge process.
 *
 * <p>Executes on a cron schedule defined by
 * {@code sfdc.governance.retention.purge.cron} (default: 2 AM UTC daily).
 * Exits immediately when {@code sfdc.governance.retention.purge.enabled} is {@code false}
 * or when the resolved purge mode is {@link PurgeMode#DISABLED}.
 *
 * <p>The scheduler is always registered in the Spring context; the enabled flag is
 * evaluated at runtime so it can be controlled via environment-specific configuration
 * without requiring a restart (when backed by a live-reload config source).
 *
 * <p>Failures during a purge run are caught here to prevent the scheduler from crashing
 * the application — the purge service itself logs and audits per-record failures.
 */
@Component
public class RetentionPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeScheduler.class);

    private final RetentionPurgeService purgeService;
    private final RetentionPurgeProperties purgeProperties;

    public RetentionPurgeScheduler(RetentionPurgeService purgeService,
                                    RetentionPurgeProperties purgeProperties) {
        this.purgeService = purgeService;
        this.purgeProperties = purgeProperties;
    }

    /**
     * Triggered on the configured cron schedule.
     * Exits immediately if the purge process is disabled or in DISABLED mode.
     * Catches all exceptions to prevent Spring's scheduler thread pool from being
     * consumed by a failed purge run.
     */
    @Scheduled(cron = "${sfdc.governance.retention.purge.cron:0 0 2 * * *}")
    public void scheduledPurge() {
        if (!purgeProperties.isEnabled()) {
            log.debug("Retention purge is disabled (enabled=false) — skipping scheduled run");
            return;
        }

        PurgeMode mode = purgeProperties.resolvedMode();
        if (mode == PurgeMode.DISABLED) {
            log.debug("Retention purge mode is DISABLED — skipping scheduled run");
            return;
        }

        log.info("Retention purge scheduled run starting: mode={} batchSize={}",
                mode, purgeProperties.getBatchSize());

        try {
            PurgeRunResult result = purgeService.runPurge();
            if (result.hasFailures()) {
                log.warn("Retention purge run completed with failures: {}", result);
            } else {
                log.info("Retention purge run completed: {}", result);
            }
        } catch (Exception ex) {
            // Prevent scheduler thread pool exhaustion — the service already logged and audited
            log.error("Retention purge scheduler caught unexpected exception — run aborted: failureCategory=SCHEDULER_ERROR");
        }
    }
}
