package com.opsera.integrator.sfdc.services.prevalidate;

/**
 * Input context for a prevalidation pass.
 *
 * <p>Callers supply either a raw package XML string or a component type and selection list.
 * When both are supplied, {@code packageXml} takes precedence. When neither is supplied,
 * prevalidation returns a hard failure.
 *
 * <p>The {@code correlationId} field is used for safe structured logging only — it is never
 * included in result payloads or API responses.
 *
 * <p>Use {@link #builder()} to construct instances.
 */
public final class PrevalidationContext {

    /** Default maximum package XML size in bytes (512 KB). */
    public static final int DEFAULT_MAX_XML_BYTES = 524288;

    private final String packageXml;
    private final String componentType;
    private final String correlationId;
    private final String operationType;
    private final int maxXmlBytes;

    private PrevalidationContext(Builder builder) {
        this.packageXml = builder.packageXml;
        this.componentType = builder.componentType;
        this.correlationId = builder.correlationId;
        this.operationType = builder.operationType;
        this.maxXmlBytes = builder.maxXmlBytes;
    }

    public String getPackageXml() { return packageXml; }
    public String getComponentType() { return componentType; }
    public String getCorrelationId() { return correlationId; }
    public String getOperationType() { return operationType; }
    public int getMaxXmlBytes() { return maxXmlBytes; }

    public boolean hasPackageXml() {
        return packageXml != null && !packageXml.isBlank();
    }

    @Override
    public String toString() {
        // Never include packageXml or componentType in toString — may contain metadata.
        return "PrevalidationContext{operationType='" + operationType
                + "', hasPackageXml=" + hasPackageXml()
                + ", maxXmlBytes=" + maxXmlBytes + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String packageXml;
        private String componentType;
        private String correlationId = "";
        private String operationType = "";
        private int maxXmlBytes = DEFAULT_MAX_XML_BYTES;

        private Builder() {}

        public Builder packageXml(String packageXml) {
            this.packageXml = packageXml;
            return this;
        }

        public Builder componentType(String componentType) {
            this.componentType = componentType;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId != null ? correlationId : "";
            return this;
        }

        public Builder operationType(String operationType) {
            this.operationType = operationType != null ? operationType : "";
            return this;
        }

        public Builder maxXmlBytes(int maxXmlBytes) {
            this.maxXmlBytes = maxXmlBytes;
            return this;
        }

        public PrevalidationContext build() {
            return new PrevalidationContext(this);
        }
    }
}
