package com.opsera.integrator.sfdc.lifecycle;

/**
 * Input value object for a lifecycle state transition request.
 *
 * <p>Safe fields only: no raw payloads, credentials, CLI output, or stack traces.
 * {@code safeReasonCode} is a machine-readable category string (e.g. "WORKER_TIMEOUT").
 * {@code safeDiagnosticSummary} is a human-readable summary capped at 1024 characters.
 */
public class TransitionRequest {

    private final String jobId;
    private final JobLifecycleState targetState;
    private final String correlationId;
    private final String safeReasonCode;
    private final String safeDiagnosticSummary;
    private final int checkpointSequenceNumber;
    private final String checkpointCode;

    private TransitionRequest(Builder builder) {
        this.jobId = builder.jobId;
        this.targetState = builder.targetState;
        this.correlationId = builder.correlationId;
        this.safeReasonCode = builder.safeReasonCode;
        this.safeDiagnosticSummary = builder.safeDiagnosticSummary;
        this.checkpointSequenceNumber = builder.checkpointSequenceNumber;
        this.checkpointCode = builder.checkpointCode;
    }

    public String getJobId() { return jobId; }
    public JobLifecycleState getTargetState() { return targetState; }
    public String getCorrelationId() { return correlationId; }
    public String getSafeReasonCode() { return safeReasonCode; }
    public String getSafeDiagnosticSummary() { return safeDiagnosticSummary; }
    public int getCheckpointSequenceNumber() { return checkpointSequenceNumber; }
    public String getCheckpointCode() { return checkpointCode; }

    public static Builder builder(String jobId, JobLifecycleState targetState) {
        return new Builder(jobId, targetState);
    }

    public static final class Builder {
        private final String jobId;
        private final JobLifecycleState targetState;
        private String correlationId;
        private String safeReasonCode;
        private String safeDiagnosticSummary;
        private int checkpointSequenceNumber = -1;
        private String checkpointCode;

        private Builder(String jobId, JobLifecycleState targetState) {
            this.jobId = jobId;
            this.targetState = targetState;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder safeReasonCode(String safeReasonCode) {
            this.safeReasonCode = safeReasonCode;
            return this;
        }

        public Builder safeDiagnosticSummary(String safeDiagnosticSummary) {
            this.safeDiagnosticSummary = safeDiagnosticSummary;
            return this;
        }

        public Builder checkpointSequenceNumber(int seq) {
            this.checkpointSequenceNumber = seq;
            return this;
        }

        public Builder checkpointCode(String code) {
            this.checkpointCode = code;
            return this;
        }

        public TransitionRequest build() {
            if (jobId == null || jobId.isBlank()) {
                throw new LifecycleTransitionException("TransitionRequest.jobId must not be null or blank");
            }
            if (targetState == null) {
                throw new LifecycleTransitionException("TransitionRequest.targetState must not be null");
            }
            return new TransitionRequest(this);
        }
    }

    @Override
    public String toString() {
        return "TransitionRequest{jobId='" + jobId + "', targetState=" + targetState +
                ", checkpointCode='" + checkpointCode + "'}";
    }
}
