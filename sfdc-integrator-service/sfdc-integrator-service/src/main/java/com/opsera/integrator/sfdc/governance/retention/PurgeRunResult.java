package com.opsera.integrator.sfdc.governance.retention;

import java.time.Instant;

/**
 * Summary statistics for a completed retention purge run.
 *
 * <p>Returned by {@link RetentionPurgeService#runPurge} and logged as a structured
 * run completion event. Safe for operational logs — no job identifiers or PII.
 */
public class PurgeRunResult {

    private final String runId;
    private final PurgeMode mode;
    private final int selectedCount;
    private final int purgedCount;
    private final int skippedCount;
    private final int failedCount;
    private final long durationMs;
    private final Instant startedAt;
    private final Instant completedAt;

    private PurgeRunResult(Builder builder) {
        this.runId = builder.runId;
        this.mode = builder.mode;
        this.selectedCount = builder.selectedCount;
        this.purgedCount = builder.purgedCount;
        this.skippedCount = builder.skippedCount;
        this.failedCount = builder.failedCount;
        this.durationMs = builder.durationMs;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
    }

    public String getRunId() { return runId; }
    public PurgeMode getMode() { return mode; }
    public int getSelectedCount() { return selectedCount; }
    public int getPurgedCount() { return purgedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getFailedCount() { return failedCount; }
    public long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public boolean hasFailures() { return failedCount > 0; }

    @Override
    public String toString() {
        return "PurgeRunResult{runId='" + runId + "', mode=" + mode +
               ", selected=" + selectedCount + ", purged=" + purgedCount +
               ", skipped=" + skippedCount + ", failed=" + failedCount +
               ", durationMs=" + durationMs + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String runId;
        private PurgeMode mode;
        private int selectedCount;
        private int purgedCount;
        private int skippedCount;
        private int failedCount;
        private long durationMs;
        private Instant startedAt;
        private Instant completedAt;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder mode(PurgeMode mode) { this.mode = mode; return this; }
        public Builder selectedCount(int selectedCount) { this.selectedCount = selectedCount; return this; }
        public Builder purgedCount(int purgedCount) { this.purgedCount = purgedCount; return this; }
        public Builder skippedCount(int skippedCount) { this.skippedCount = skippedCount; return this; }
        public Builder failedCount(int failedCount) { this.failedCount = failedCount; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public PurgeRunResult build() { return new PurgeRunResult(this); }
    }
}
