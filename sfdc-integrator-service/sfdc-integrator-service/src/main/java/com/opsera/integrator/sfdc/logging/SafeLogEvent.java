package com.opsera.integrator.sfdc.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable structured log event carrying only allow-listed operational metadata.
 *
 * <p>Controllers build events explicitly using the {@link Builder} — no DTO
 * toString is ever called. Sensitive field names and raw request content are
 * excluded at construction time by the caller; {@link SafeStructuredLogger}
 * applies a second-pass filter on key names before emitting the message.
 */
public final class SafeLogEvent {

    /**
     * Semantic outcome for an operational log event.
     * Use {@code ACCEPTED}/{@code COMPLETED} for successful paths (→ INFO),
     * {@code REJECTED}/{@code FAILED} for error or degraded paths (→ WARN).
     */
    public enum Outcome {
        ACCEPTED, COMPLETED, REJECTED, FAILED
    }

    private final String operation;
    private final String controller;
    private final String correlationId;
    private final Outcome outcome;
    private final Map<String, String> safeFields;
    private final boolean extractionFailed;

    private SafeLogEvent(Builder builder) {
        this.operation = builder.operation;
        this.controller = builder.controller;
        this.correlationId = builder.correlationId;
        this.outcome = builder.outcome;
        this.safeFields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.safeFields));
        this.extractionFailed = builder.extractionFailed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOperation() { return operation; }
    public String getController() { return controller; }
    public String getCorrelationId() { return correlationId; }
    public Outcome getOutcome() { return outcome; }
    public Map<String, String> getSafeFields() { return safeFields; }
    public boolean isExtractionFailed() { return extractionFailed; }

    public static final class Builder {
        private String operation = "unknown";
        private String controller = "unknown";
        private String correlationId;
        private Outcome outcome = Outcome.ACCEPTED;
        private final Map<String, String> safeFields = new LinkedHashMap<>();
        private boolean extractionFailed;

        public Builder operation(String operation) {
            this.operation = operation != null ? operation : "unknown";
            return this;
        }

        public Builder controller(String controller) {
            this.controller = controller != null ? controller : "unknown";
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder outcome(Outcome outcome) {
            this.outcome = outcome != null ? outcome : Outcome.ACCEPTED;
            return this;
        }

        public Builder safeField(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                this.safeFields.put(key, value);
            }
            return this;
        }

        public Builder extractionFailed(boolean failed) {
            this.extractionFailed = failed;
            return this;
        }

        public SafeLogEvent build() {
            return new SafeLogEvent(this);
        }
    }
}
