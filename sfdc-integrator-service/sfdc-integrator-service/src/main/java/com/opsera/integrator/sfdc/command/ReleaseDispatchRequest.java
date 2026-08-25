package com.opsera.integrator.sfdc.command;

/**
 * Internal command DTO for routing a release operation to worker or legacy execution.
 *
 * <p>All fields are safe, operational identifiers. Raw Salesforce credentials, bearer tokens,
 * org URLs, and unrestricted user data must never be stored here. Callers must hash or omit
 * customer identifiers before constructing this object.
 */
public final class ReleaseDispatchRequest {

    private final String commandType;
    private final String correlationId;
    private final String idempotencyKey;
    private final String pipelineId;
    private final String stepId;
    /** Opaque customer reference — callers must not pass raw PII or credentials here. */
    private final String customerId;
    private final String sfdcToolId;
    private final String deploymentRequestId;
    private final String fallbackTaskId;
    private final int timeoutSeconds;
    /** Allow-listed metadata JSON — must be produced via SafeAuditMetadata or equivalent. */
    private final String safeMetadata;

    private ReleaseDispatchRequest(Builder builder) {
        this.commandType = builder.commandType;
        this.correlationId = builder.correlationId;
        this.idempotencyKey = builder.idempotencyKey;
        this.pipelineId = builder.pipelineId;
        this.stepId = builder.stepId;
        this.customerId = builder.customerId;
        this.sfdcToolId = builder.sfdcToolId;
        this.deploymentRequestId = builder.deploymentRequestId;
        this.fallbackTaskId = builder.fallbackTaskId;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.safeMetadata = builder.safeMetadata;
    }

    public String getCommandType() { return commandType; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPipelineId() { return pipelineId; }
    public String getStepId() { return stepId; }
    public String getCustomerId() { return customerId; }
    public String getSfdcToolId() { return sfdcToolId; }
    public String getDeploymentRequestId() { return deploymentRequestId; }
    public String getFallbackTaskId() { return fallbackTaskId; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getSafeMetadata() { return safeMetadata; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String commandType;
        private String correlationId;
        private String idempotencyKey;
        private String pipelineId;
        private String stepId;
        private String customerId;
        private String sfdcToolId;
        private String deploymentRequestId;
        private String fallbackTaskId = "";
        private int timeoutSeconds = 1800;
        private String safeMetadata;

        public Builder commandType(String commandType) { this.commandType = commandType; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder pipelineId(String pipelineId) { this.pipelineId = pipelineId; return this; }
        public Builder stepId(String stepId) { this.stepId = stepId; return this; }
        public Builder customerId(String customerId) { this.customerId = customerId; return this; }
        public Builder sfdcToolId(String sfdcToolId) { this.sfdcToolId = sfdcToolId; return this; }
        public Builder deploymentRequestId(String deploymentRequestId) { this.deploymentRequestId = deploymentRequestId; return this; }
        public Builder fallbackTaskId(String fallbackTaskId) { this.fallbackTaskId = fallbackTaskId; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder safeMetadata(String safeMetadata) { this.safeMetadata = safeMetadata; return this; }

        public ReleaseDispatchRequest build() {
            if (commandType == null || commandType.isBlank()) {
                throw new IllegalArgumentException("commandType must not be null or blank");
            }
            return new ReleaseDispatchRequest(this);
        }
    }

    @Override
    public String toString() {
        return "ReleaseDispatchRequest{commandType='" + commandType
                + "', correlationId='" + correlationId
                + "', pipelineId='" + pipelineId
                + "', stepId='" + stepId
                + "', timeoutSeconds=" + timeoutSeconds + '}';
    }
}
