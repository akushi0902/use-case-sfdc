package com.opsera.integrator.sfdc.command;

/**
 * Immutable value object carrying validated metadata for an asynchronous release command.
 *
 * <p>All caller-controlled identifiers must be validated by the submitting layer (controller
 * or service) before constructing this object. Raw Salesforce credentials, bearer tokens,
 * Org URLs, and customer PII are never stored here — only safe, hashed, or opaque references.
 *
 * <p>The {@link #idempotencyKey} is used by {@link CommandDispatcher} to detect duplicate
 * submissions and return the original accepted outcome when safe.
 */
public final class AsyncCommandRequest {

    private final String operationType;
    private final String correlationId;
    private final String idempotencyKey;
    private final String pipelineId;
    private final String stepId;
    /** SHA-256 hex of the customer identifier; never the raw value. */
    private final String customerIdHash;
    private final String sfdcToolId;
    private final String deploymentRequestId;
    private final String dataClassification;

    private AsyncCommandRequest(Builder builder) {
        this.operationType = builder.operationType;
        this.correlationId = builder.correlationId;
        this.idempotencyKey = builder.idempotencyKey;
        this.pipelineId = builder.pipelineId;
        this.stepId = builder.stepId;
        this.customerIdHash = builder.customerIdHash;
        this.sfdcToolId = builder.sfdcToolId;
        this.deploymentRequestId = builder.deploymentRequestId;
        this.dataClassification = builder.dataClassification;
    }

    public String getOperationType() { return operationType; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPipelineId() { return pipelineId; }
    public String getStepId() { return stepId; }
    public String getCustomerIdHash() { return customerIdHash; }
    public String getSfdcToolId() { return sfdcToolId; }
    public String getDeploymentRequestId() { return deploymentRequestId; }
    public String getDataClassification() { return dataClassification; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String operationType;
        private String correlationId;
        private String idempotencyKey;
        private String pipelineId;
        private String stepId;
        private String customerIdHash;
        private String sfdcToolId;
        private String deploymentRequestId;
        private String dataClassification = "CONFIDENTIAL";

        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder pipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder customerIdHash(String customerIdHash) {
            this.customerIdHash = customerIdHash;
            return this;
        }

        public Builder sfdcToolId(String sfdcToolId) {
            this.sfdcToolId = sfdcToolId;
            return this;
        }

        public Builder deploymentRequestId(String deploymentRequestId) {
            this.deploymentRequestId = deploymentRequestId;
            return this;
        }

        public Builder dataClassification(String dataClassification) {
            this.dataClassification = dataClassification;
            return this;
        }

        public AsyncCommandRequest build() {
            return new AsyncCommandRequest(this);
        }
    }

    @Override
    public String toString() {
        return "AsyncCommandRequest{operationType='" + operationType
                + "', correlationId='" + correlationId
                + "', pipelineId='" + pipelineId
                + "', stepId='" + stepId
                + "'}";
    }
}
