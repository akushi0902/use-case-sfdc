package com.opsera.integrator.sfdc.governance.audit;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;

import java.time.Instant;

/**
 * Immutable domain object representing a single audit event.
 *
 * <p>Constructed via {@link #builder()} — no setters are exposed to prevent
 * post-construction mutation. All fields except optional ones are required.
 *
 * <p>Safe metadata must be built with {@link SafeAuditMetadata.Builder} before
 * calling {@link Builder#safeMetadata(String)} to ensure only allow-listed
 * keys are stored.
 *
 * <p>The {@code toString()} implementation omits {@code actorRef} and
 * {@code resourceRef} to prevent accidental log exposure of identifiers.
 */
public final class AuditEvent {

    private final String eventId;
    private final String correlationId;
    private final AuditActorType actorType;
    private final String actorRef;
    private final AuditResourceType resourceType;
    private final String resourceRef;
    private final AuditOperation operation;
    private final DataClassification classification;
    private final String safeMetadata;
    private final Instant eventTimestamp;
    private final String requestSource;
    private final Instant retentionExpiryAt;

    private AuditEvent(Builder b) {
        this.eventId = b.eventId;
        this.correlationId = b.correlationId;
        this.actorType = b.actorType;
        this.actorRef = b.actorRef;
        this.resourceType = b.resourceType;
        this.resourceRef = b.resourceRef;
        this.operation = b.operation;
        this.classification = b.classification;
        this.safeMetadata = b.safeMetadata;
        this.eventTimestamp = b.eventTimestamp;
        this.requestSource = b.requestSource;
        this.retentionExpiryAt = b.retentionExpiryAt;
    }

    public String getEventId() { return eventId; }
    public String getCorrelationId() { return correlationId; }
    public AuditActorType getActorType() { return actorType; }
    public String getActorRef() { return actorRef; }
    public AuditResourceType getResourceType() { return resourceType; }
    public String getResourceRef() { return resourceRef; }
    public AuditOperation getOperation() { return operation; }
    public DataClassification getClassification() { return classification; }
    public String getSafeMetadata() { return safeMetadata; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public String getRequestSource() { return requestSource; }
    public Instant getRetentionExpiryAt() { return retentionExpiryAt; }

    /** Safe log representation — omits actorRef and resourceRef. */
    @Override
    public String toString() {
        return "AuditEvent{eventId='" + eventId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", operation=" + operation +
                ", resourceType=" + resourceType +
                ", classification=" + classification + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String eventId;
        private String correlationId;
        private AuditActorType actorType;
        private String actorRef;
        private AuditResourceType resourceType;
        private String resourceRef;
        private AuditOperation operation;
        private DataClassification classification = DataClassification.CONFIDENTIAL;
        private String safeMetadata;
        private Instant eventTimestamp;
        private String requestSource;
        private Instant retentionExpiryAt;

        private Builder() {}

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder actorType(AuditActorType actorType) {
            this.actorType = actorType;
            return this;
        }

        public Builder actorRef(String actorRef) {
            this.actorRef = actorRef;
            return this;
        }

        public Builder resourceType(AuditResourceType resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceRef(String resourceRef) {
            this.resourceRef = resourceRef;
            return this;
        }

        public Builder operation(AuditOperation operation) {
            this.operation = operation;
            return this;
        }

        public Builder classification(DataClassification classification) {
            this.classification = classification != null ? classification : DataClassification.CONFIDENTIAL;
            return this;
        }

        /** Safe metadata JSON string — must be built via {@link SafeAuditMetadata.Builder}. */
        public Builder safeMetadata(String safeMetadata) {
            this.safeMetadata = safeMetadata;
            return this;
        }

        public Builder eventTimestamp(Instant eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
            return this;
        }

        public Builder requestSource(String requestSource) {
            this.requestSource = requestSource;
            return this;
        }

        public Builder retentionExpiryAt(Instant retentionExpiryAt) {
            this.retentionExpiryAt = retentionExpiryAt;
            return this;
        }

        public AuditEvent build() {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalStateException("AuditEvent requires a non-blank eventId");
            }
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalStateException("AuditEvent requires a non-blank correlationId");
            }
            if (actorType == null) {
                throw new IllegalStateException("AuditEvent requires an actorType");
            }
            if (actorRef == null || actorRef.isBlank()) {
                throw new IllegalStateException("AuditEvent requires a non-blank actorRef");
            }
            if (resourceType == null) {
                throw new IllegalStateException("AuditEvent requires a resourceType");
            }
            if (resourceRef == null || resourceRef.isBlank()) {
                throw new IllegalStateException("AuditEvent requires a non-blank resourceRef");
            }
            if (operation == null) {
                throw new IllegalStateException("AuditEvent requires an operation");
            }
            if (eventTimestamp == null) {
                eventTimestamp = Instant.now();
            }
            return new AuditEvent(this);
        }
    }
}
