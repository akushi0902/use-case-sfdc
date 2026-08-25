package com.opsera.integrator.sfdc.command;

import java.time.Instant;

/**
 * Result returned when a release command is submitted to {@link ReleaseCommandDispatcher}.
 *
 * <p>The {@code routedTo} field indicates which execution path was used: {@code "WORKER"} for
 * Kubernetes worker dispatch or {@code "LEGACY"} for the existing service-based execution path.
 */
public final class ReleaseDispatchResult {

    public static final String ROUTED_TO_WORKER = "WORKER";
    public static final String ROUTED_TO_LEGACY = "LEGACY";

    private final String jobId;
    private final String correlationId;
    private final String commandType;
    private final String routedTo;
    private final String statusPath;
    private final Instant acceptedAt;
    private final boolean idempotentReplay;

    private ReleaseDispatchResult(Builder builder) {
        this.jobId = builder.jobId;
        this.correlationId = builder.correlationId;
        this.commandType = builder.commandType;
        this.routedTo = builder.routedTo;
        this.statusPath = builder.statusPath;
        this.acceptedAt = builder.acceptedAt;
        this.idempotentReplay = builder.idempotentReplay;
    }

    public String getJobId() { return jobId; }
    public String getCorrelationId() { return correlationId; }
    public String getCommandType() { return commandType; }
    public String getRoutedTo() { return routedTo; }
    public String getStatusPath() { return statusPath; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public boolean isIdempotentReplay() { return idempotentReplay; }

    public boolean wasWorkerDispatched() {
        return ROUTED_TO_WORKER.equals(routedTo);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String jobId;
        private String correlationId;
        private String commandType;
        private String routedTo = ROUTED_TO_LEGACY;
        private String statusPath;
        private Instant acceptedAt = Instant.now();
        private boolean idempotentReplay;

        public Builder jobId(String jobId) { this.jobId = jobId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder commandType(String commandType) { this.commandType = commandType; return this; }
        public Builder routedTo(String routedTo) { this.routedTo = routedTo; return this; }
        public Builder statusPath(String statusPath) { this.statusPath = statusPath; return this; }
        public Builder acceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; return this; }
        public Builder idempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; return this; }

        public ReleaseDispatchResult build() {
            return new ReleaseDispatchResult(this);
        }
    }

    @Override
    public String toString() {
        return "ReleaseDispatchResult{jobId='" + jobId
                + "', correlationId='" + correlationId
                + "', commandType='" + commandType
                + "', routedTo='" + routedTo
                + "', idempotentReplay=" + idempotentReplay + '}';
    }
}
