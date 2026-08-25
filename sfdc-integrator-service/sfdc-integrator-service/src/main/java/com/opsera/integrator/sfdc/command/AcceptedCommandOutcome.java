package com.opsera.integrator.sfdc.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result returned by {@link CommandDispatcher#dispatch(AsyncCommandRequest)} when
 * a command is durably accepted.
 *
 * <p>Callers use {@code statusPath} to construct subsequent job-status poll requests.
 * The {@code idempotentReplay} flag indicates whether an existing accepted job was returned
 * rather than a newly created one.
 *
 * <p>No internal worker hostnames, raw credentials, full payloads, or stack traces are
 * included in any field.
 */
public final class AcceptedCommandOutcome {

    private final String jobId;
    private final String correlationId;
    private final String statusPath;
    private final String state;
    private final Instant acceptedAt;
    private final String operationType;
    private final boolean idempotentReplay;
    private final List<String> safeWarnings;

    private AcceptedCommandOutcome(Builder builder) {
        this.jobId = builder.jobId;
        this.correlationId = builder.correlationId;
        this.statusPath = builder.statusPath;
        this.state = builder.state;
        this.acceptedAt = builder.acceptedAt;
        this.operationType = builder.operationType;
        this.idempotentReplay = builder.idempotentReplay;
        this.safeWarnings = Collections.unmodifiableList(new ArrayList<>(builder.safeWarnings));
    }

    public String getJobId() { return jobId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatusPath() { return statusPath; }
    public String getState() { return state; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getOperationType() { return operationType; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
    public List<String> getSafeWarnings() { return safeWarnings; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String jobId;
        private String correlationId;
        private String statusPath;
        private String state = "ACCEPTED";
        private Instant acceptedAt = Instant.now();
        private String operationType;
        private boolean idempotentReplay;
        private List<String> safeWarnings = new ArrayList<>();

        public Builder jobId(String jobId) { this.jobId = jobId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder statusPath(String statusPath) { this.statusPath = statusPath; return this; }
        public Builder state(String state) { this.state = state; return this; }
        public Builder acceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; return this; }
        public Builder operationType(String operationType) { this.operationType = operationType; return this; }
        public Builder idempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; return this; }
        public Builder safeWarnings(List<String> safeWarnings) {
            this.safeWarnings = safeWarnings != null ? new ArrayList<>(safeWarnings) : new ArrayList<>();
            return this;
        }
        public Builder addWarning(String warning) {
            if (warning != null && !warning.isBlank()) {
                this.safeWarnings.add(warning);
            }
            return this;
        }

        public AcceptedCommandOutcome build() {
            return new AcceptedCommandOutcome(this);
        }
    }

    @Override
    public String toString() {
        return "AcceptedCommandOutcome{jobId='" + jobId
                + "', correlationId='" + correlationId
                + "', state='" + state
                + "', operationType='" + operationType
                + "', idempotentReplay=" + idempotentReplay + '}';
    }
}
