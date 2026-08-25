package com.opsera.integrator.sfdc.lifecycle;

import java.time.Instant;

/**
 * Immutable result of a successful or idempotent lifecycle state transition.
 *
 * <p>{@code idempotentReplay} is {@code true} when the job was already in
 * {@code toState} and no database mutation was performed.
 */
public final class TransitionResult {

    private final String jobId;
    private final JobLifecycleState fromState;
    private final JobLifecycleState toState;
    private final String correlationId;
    private final Instant transitionedAt;
    private final long newVersion;
    private final boolean idempotentReplay;

    private TransitionResult(Builder builder) {
        this.jobId = builder.jobId;
        this.fromState = builder.fromState;
        this.toState = builder.toState;
        this.correlationId = builder.correlationId;
        this.transitionedAt = builder.transitionedAt;
        this.newVersion = builder.newVersion;
        this.idempotentReplay = builder.idempotentReplay;
    }

    public String getJobId() { return jobId; }
    public JobLifecycleState getFromState() { return fromState; }
    public JobLifecycleState getToState() { return toState; }
    public String getCorrelationId() { return correlationId; }
    public Instant getTransitionedAt() { return transitionedAt; }
    public long getNewVersion() { return newVersion; }
    public boolean isIdempotentReplay() { return idempotentReplay; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String jobId;
        private JobLifecycleState fromState;
        private JobLifecycleState toState;
        private String correlationId;
        private Instant transitionedAt = Instant.now();
        private long newVersion;
        private boolean idempotentReplay;

        public Builder jobId(String jobId) { this.jobId = jobId; return this; }
        public Builder fromState(JobLifecycleState s) { this.fromState = s; return this; }
        public Builder toState(JobLifecycleState s) { this.toState = s; return this; }
        public Builder correlationId(String id) { this.correlationId = id; return this; }
        public Builder transitionedAt(Instant t) { this.transitionedAt = t; return this; }
        public Builder newVersion(long v) { this.newVersion = v; return this; }
        public Builder idempotentReplay(boolean b) { this.idempotentReplay = b; return this; }
        public TransitionResult build() { return new TransitionResult(this); }
    }

    @Override
    public String toString() {
        return "TransitionResult{jobId='" + jobId + "', from=" + fromState +
                ", to=" + toState + ", version=" + newVersion +
                ", idempotent=" + idempotentReplay + '}';
    }
}
