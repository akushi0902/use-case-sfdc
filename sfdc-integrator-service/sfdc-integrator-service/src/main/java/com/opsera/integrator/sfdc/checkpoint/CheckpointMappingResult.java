package com.opsera.integrator.sfdc.checkpoint;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;

/**
 * Result of mapping a {@link CheckpointEvent} to lifecycle persistence objects.
 *
 * <p>A mapping result carries:
 * <ul>
 *   <li>A {@link JobCheckpoint} ready for persistence (always present for valid events).</li>
 *   <li>An optional {@link CheckpointStateTransition} describing the lifecycle state
 *       change implied by this event. {@code null} means no state change — checkpoint-only
 *       persistence (e.g. PROGRESS events).</li>
 *   <li>A {@link MappingOutcome} indicating whether the event was valid, skippable, or orphaned.</li>
 *   <li>A deterministic event key for duplicate detection.</li>
 * </ul>
 */
public final class CheckpointMappingResult {

    public enum MappingOutcome {
        /** Event mapped and should be persisted normally. */
        PERSIST,
        /** Event is a duplicate of an already-persisted record (same event key). */
        DUPLICATE,
        /** Job referenced by jobId was not found — event is orphaned. */
        ORPHANED,
        /** Event is malformed or missing required fields — skip with safe log. */
        MALFORMED
    }

    private final MappingOutcome outcome;
    private final JobCheckpoint checkpoint;
    private final CheckpointStateTransition stateTransition;
    private final String eventKey;
    private final String safeSkipReason;

    private CheckpointMappingResult(Builder builder) {
        this.outcome = builder.outcome;
        this.checkpoint = builder.checkpoint;
        this.stateTransition = builder.stateTransition;
        this.eventKey = builder.eventKey;
        this.safeSkipReason = builder.safeSkipReason;
    }

    public MappingOutcome getOutcome() { return outcome; }
    public JobCheckpoint getCheckpoint() { return checkpoint; }
    public CheckpointStateTransition getStateTransition() { return stateTransition; }
    public String getEventKey() { return eventKey; }
    public String getSafeSkipReason() { return safeSkipReason; }

    public boolean requiresPersistence() { return outcome == MappingOutcome.PERSIST; }
    public boolean hasStateTransition() { return stateTransition != null; }

    public static Builder builder(MappingOutcome outcome) {
        return new Builder(outcome);
    }

    public static final class Builder {
        private final MappingOutcome outcome;
        private JobCheckpoint checkpoint;
        private CheckpointStateTransition stateTransition;
        private String eventKey;
        private String safeSkipReason;

        private Builder(MappingOutcome outcome) { this.outcome = outcome; }

        public Builder checkpoint(JobCheckpoint checkpoint) { this.checkpoint = checkpoint; return this; }
        public Builder stateTransition(CheckpointStateTransition t) { this.stateTransition = t; return this; }
        public Builder eventKey(String k) { this.eventKey = k; return this; }
        public Builder safeSkipReason(String r) { this.safeSkipReason = r; return this; }
        public CheckpointMappingResult build() { return new CheckpointMappingResult(this); }
    }

    @Override
    public String toString() {
        return "CheckpointMappingResult{outcome=" + outcome + ", eventKey='" + eventKey + "'}";
    }
}
