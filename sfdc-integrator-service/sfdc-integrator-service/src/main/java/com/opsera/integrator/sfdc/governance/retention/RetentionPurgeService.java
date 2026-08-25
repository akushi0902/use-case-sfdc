package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.audit.AuditActorType;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.audit.SafeAuditMetadata;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes the automated retention purge process for expired governed records.
 *
 * <p>Purge runs in three modes, controlled by {@link RetentionPurgeProperties}:
 * <ul>
 *   <li>{@link PurgeMode#DISABLED} — exits immediately; no queries or mutations.</li>
 *   <li>{@link PurgeMode#DRY_RUN} — selects candidates and emits audit events but does NOT erase data.</li>
 *   <li>{@link PurgeMode#ENFORCE} — selects candidates and physically erases governed data via cryptographic
 *       erasure semantics (sensitive fields nulled in-place; child records deleted).</li>
 * </ul>
 *
 * <p>Eligibility criteria (all must be true):
 * <ol>
 *   <li>Retention expiration ({@code purge_eligible_at}) has passed.</li>
 *   <li>{@code purge_eligibility_status} is {@link PurgeEligibilityStatus#ELIGIBLE}.</li>
 *   <li>{@code legal_hold} is {@code false}.</li>
 *   <li>{@code purged_at} is {@code null} (not already purged).</li>
 * </ol>
 *
 * <p>An application-level lock ({@link AtomicBoolean}) prevents concurrent purge runs within
 * a single JVM. In multi-pod deployments a database advisory lock should be added.
 *
 * <p>Each run emits three audit events:
 * {@code PURGE_RUN_START}, per-batch {@code BATCH_RESULT}, and {@code PURGE_RUN_COMPLETE}.
 * All use {@link AuditOperation#PURGE_ACTION} and {@link AuditResourceType#PURGE_RUN}.
 */
@Service
public class RetentionPurgeService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeService.class);

    private final RetentionMetadataRepository retentionRepository;
    private final JobRepository jobRepository;
    private final AuditEventWriter auditWriter;
    private final SafeStructuredLogger safeLogger;
    private final RetentionPurgeProperties purgeProperties;
    private final Clock clock;

    /**
     * Application-level lock preventing concurrent purge runs within a single JVM.
     * Set to true when a run is in progress; reset in a finally block.
     */
    private final AtomicBoolean runLock = new AtomicBoolean(false);

    public RetentionPurgeService(RetentionMetadataRepository retentionRepository,
                                  JobRepository jobRepository,
                                  AuditEventWriter auditWriter,
                                  SafeStructuredLogger safeLogger,
                                  RetentionPurgeProperties purgeProperties,
                                  Clock clock) {
        this.retentionRepository = retentionRepository;
        this.jobRepository = jobRepository;
        this.auditWriter = auditWriter;
        this.safeLogger = safeLogger;
        this.purgeProperties = purgeProperties;
        this.clock = clock;
    }

    /**
     * Executes a purge run using the configured mode and batch size from properties.
     *
     * @return result summarising selected, purged, skipped, and failed counts
     */
    public PurgeRunResult runPurge() {
        return runPurge(purgeProperties.resolvedMode(), purgeProperties.getBatchSize());
    }

    /**
     * Executes a purge run with explicit mode and batch size (used directly by tests and
     * caller-overrides without requiring a full property reload).
     *
     * @param mode      purge mode; must not be null
     * @param batchSize maximum candidates to process; capped at 1000 as a safety upper bound
     * @return result summarising selected, purged, skipped, and failed counts
     */
    public PurgeRunResult runPurge(PurgeMode mode, int batchSize) {
        if (mode == null || mode == PurgeMode.DISABLED) {
            log.debug("Retention purge is DISABLED — skipping run");
            Instant now = Instant.now(clock);
            return PurgeRunResult.builder()
                    .runId("disabled")
                    .mode(PurgeMode.DISABLED)
                    .startedAt(now).completedAt(now)
                    .build();
        }

        if (!runLock.compareAndSet(false, true)) {
            log.warn("Retention purge already running — concurrent run rejected (runLock held)");
            safeLogger.logEvent(SafeLogEvent.builder()
                    .operation("retention-purge")
                    .controller("RetentionPurgeService")
                    .outcome(SafeLogEvent.Outcome.REJECTED)
                    .safeField("reason", "concurrent-run-rejected")
                    .build());
            Instant now = Instant.now(clock);
            return PurgeRunResult.builder()
                    .runId("lock-rejected")
                    .mode(mode)
                    .startedAt(now).completedAt(now)
                    .build();
        }

        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now(clock);
        int effectiveBatchSize = Math.min(batchSize, 1000);

        log.info("Retention purge run starting: runId={} mode={} batchSize={}", runId, mode, effectiveBatchSize);

        int purgedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<RetentionMetadata> candidates = List.of();

        try {
            emitRunStartAudit(runId, mode, effectiveBatchSize);

            candidates = retentionRepository.findPurgeCandidates(startedAt, effectiveBatchSize);
            int selectedCount = candidates.size();

            log.info("Retention purge candidates selected: runId={} selected={} mode={}", runId, selectedCount, mode);

            if (mode == PurgeMode.DRY_RUN) {
                // Dry-run: count only, no mutations
                skippedCount = selectedCount;
                emitBatchAudit(runId, mode, 1, selectedCount, 0, selectedCount, 0);
            } else {
                // ENFORCE mode: erase eligible records
                int batchNum = 0;
                for (RetentionMetadata candidate : candidates) {
                    // Double-check legal hold (fail-closed: skip if legal hold or ELIGIBLE status changed)
                    if (candidate.isLegalHold() || candidate.getPurgeEligibilityStatus() != PurgeEligibilityStatus.ELIGIBLE) {
                        log.debug("Skipping protected record: runId={} reason=legal-hold-or-status-changed", runId);
                        skippedCount++;
                        continue;
                    }

                    Instant deadline = startedAt.plusSeconds(purgeProperties.getMaxRuntimeSeconds());
                    if (Instant.now(clock).isAfter(deadline)) {
                        log.warn("Retention purge run exceeded max runtime: runId={} deadline={}", runId, deadline);
                        break;
                    }

                    boolean erased = eraseRecord(runId, candidate);
                    if (erased) {
                        purgedCount++;
                    } else {
                        failedCount++;
                    }

                    batchNum++;
                    if (batchNum % 10 == 0) {
                        emitBatchAudit(runId, mode, batchNum / 10, 10, purgedCount, skippedCount, failedCount);
                    }
                }
                // Emit final batch event
                emitBatchAudit(runId, mode, candidates.size(), selectedCount, purgedCount, skippedCount, failedCount);
            }

            long durationMs = Instant.now(clock).toEpochMilli() - startedAt.toEpochMilli();
            PurgeRunResult result = PurgeRunResult.builder()
                    .runId(runId)
                    .mode(mode)
                    .selectedCount(candidates.size())
                    .purgedCount(purgedCount)
                    .skippedCount(skippedCount)
                    .failedCount(failedCount)
                    .durationMs(durationMs)
                    .startedAt(startedAt)
                    .completedAt(Instant.now(clock))
                    .build();

            emitRunCompleteAudit(runId, mode, result);

            log.info("Retention purge run completed: {}", result);
            safeLogger.logEvent(SafeLogEvent.builder()
                    .operation("retention-purge")
                    .controller("RetentionPurgeService")
                    .outcome(failedCount > 0 ? SafeLogEvent.Outcome.FAILED : SafeLogEvent.Outcome.COMPLETED)
                    .safeField("mode", mode.name())
                    .safeField("purgedCount", String.valueOf(purgedCount))
                    .safeField("skippedCount", String.valueOf(skippedCount))
                    .safeField("failedCount", String.valueOf(failedCount))
                    .build());

            return result;

        } finally {
            runLock.set(false);
        }
    }

    // ---- private helpers ----

    private boolean eraseRecord(String runId, RetentionMetadata candidate) {
        String jobRef = candidate.getJobRef();
        try {
            jobRepository.eraseJobData(jobRef);
            retentionRepository.markPurged(jobRef, Instant.now(clock));
            return true;
        } catch (LifecyclePersistenceException ex) {
            log.warn("Failed to erase job data: runId={} failureCategory=PERSISTENCE_FAILURE", runId);
            return false;
        } catch (Exception ex) {
            log.warn("Unexpected error erasing job data: runId={} failureCategory=UNEXPECTED_FAILURE", runId);
            return false;
        }
    }

    private void emitRunStartAudit(String runId, PurgeMode mode, int batchSize) {
        try {
            auditWriter.write(
                    AuditActorType.SYSTEM,
                    "retention-purge-scheduler",
                    AuditResourceType.PURGE_RUN,
                    runId,
                    AuditOperation.PURGE_ACTION,
                    DataClassification.CONFIDENTIAL,
                    SafeAuditMetadata.builder()
                            .field("runId", runId)
                            .field("mode", mode.name())
                            .field("batchSize", String.valueOf(batchSize))
                            .field("outcome", "RUN_STARTED")
                            .toJson(),
                    "RetentionPurgeService.runPurge");
        } catch (Exception ex) {
            log.warn("Failed to emit purge run-start audit event: runId={}", runId);
        }
    }

    private void emitBatchAudit(String runId, PurgeMode mode, int batchNumber,
                                  int selectedCount, int purgedCount, int skippedCount, int failedCount) {
        try {
            auditWriter.write(
                    AuditActorType.SYSTEM,
                    "retention-purge-scheduler",
                    AuditResourceType.PURGE_RUN,
                    runId,
                    AuditOperation.PURGE_ACTION,
                    DataClassification.CONFIDENTIAL,
                    SafeAuditMetadata.builder()
                            .field("runId", runId)
                            .field("mode", mode.name())
                            .field("batchNumber", String.valueOf(batchNumber))
                            .field("selectedCount", String.valueOf(selectedCount))
                            .field("purgedCount", String.valueOf(purgedCount))
                            .field("skippedCount", String.valueOf(skippedCount))
                            .field("failedCount", String.valueOf(failedCount))
                            .field("outcome", "BATCH_RESULT")
                            .toJson(),
                    "RetentionPurgeService.runPurge");
        } catch (Exception ex) {
            log.warn("Failed to emit purge batch audit event: runId={} batch={}", runId, batchNumber);
        }
    }

    private void emitRunCompleteAudit(String runId, PurgeMode mode, PurgeRunResult result) {
        try {
            auditWriter.write(
                    AuditActorType.SYSTEM,
                    "retention-purge-scheduler",
                    AuditResourceType.PURGE_RUN,
                    runId,
                    AuditOperation.PURGE_ACTION,
                    DataClassification.CONFIDENTIAL,
                    SafeAuditMetadata.builder()
                            .field("runId", runId)
                            .field("mode", mode.name())
                            .field("selectedCount", String.valueOf(result.getSelectedCount()))
                            .field("purgedCount", String.valueOf(result.getPurgedCount()))
                            .field("skippedCount", String.valueOf(result.getSkippedCount()))
                            .field("failedCount", String.valueOf(result.getFailedCount()))
                            .field("durationMs", String.valueOf(result.getDurationMs()))
                            .field("outcome", result.hasFailures() ? "COMPLETED_WITH_FAILURES" : "COMPLETED")
                            .toJson(),
                    "RetentionPurgeService.runPurge");
        } catch (Exception ex) {
            log.warn("Failed to emit purge run-complete audit event: runId={}", runId);
        }
    }
}
