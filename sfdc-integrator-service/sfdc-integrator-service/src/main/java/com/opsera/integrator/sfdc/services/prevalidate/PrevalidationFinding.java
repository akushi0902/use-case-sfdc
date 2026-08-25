package com.opsera.integrator.sfdc.services.prevalidate;

/**
 * A single typed finding produced during package XML or component prevalidation.
 *
 * <p>All fields are safe for logging and API responses:
 * <ul>
 *   <li>{@link #safeComponentRef} is a safe bounded reference — never a raw XML fragment,
 *       Salesforce credential, or raw user-supplied string.</li>
 *   <li>{@link #safeSummary} is an allow-listed message template — not interpolated from
 *       raw input values.</li>
 * </ul>
 *
 * <p>Use {@link #builder()} to construct instances.
 */
public final class PrevalidationFinding {

    private final FindingSeverity severity;
    private final String componentType;
    private final String safeComponentRef;
    private final String messageCode;
    private final String safeSummary;
    private final String remediationHint;

    private PrevalidationFinding(Builder builder) {
        this.severity = builder.severity;
        this.componentType = builder.componentType;
        this.safeComponentRef = builder.safeComponentRef;
        this.messageCode = builder.messageCode;
        this.safeSummary = builder.safeSummary;
        this.remediationHint = builder.remediationHint;
    }

    public FindingSeverity getSeverity() { return severity; }
    public String getComponentType() { return componentType; }
    public String getSafeComponentRef() { return safeComponentRef; }
    public String getMessageCode() { return messageCode; }
    public String getSafeSummary() { return safeSummary; }
    public String getRemediationHint() { return remediationHint; }

    public boolean isHardFailure() {
        return FindingSeverity.HARD_FAILURE.equals(severity);
    }

    @Override
    public String toString() {
        return "PrevalidationFinding{severity=" + severity
                + ", componentType='" + componentType + '\''
                + ", messageCode='" + messageCode + '\''
                + ", safeSummary='" + safeSummary + "'}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FindingSeverity severity;
        private String componentType = "";
        private String safeComponentRef = "";
        private String messageCode = "";
        private String safeSummary = "";
        private String remediationHint = "";

        private Builder() {}

        public Builder severity(FindingSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder componentType(String componentType) {
            this.componentType = componentType != null ? componentType : "";
            return this;
        }

        public Builder safeComponentRef(String safeComponentRef) {
            this.safeComponentRef = safeComponentRef != null ? safeComponentRef : "";
            return this;
        }

        public Builder messageCode(String messageCode) {
            this.messageCode = messageCode != null ? messageCode : "";
            return this;
        }

        public Builder safeSummary(String safeSummary) {
            this.safeSummary = safeSummary != null ? safeSummary : "";
            return this;
        }

        public Builder remediationHint(String remediationHint) {
            this.remediationHint = remediationHint != null ? remediationHint : "";
            return this;
        }

        public PrevalidationFinding build() {
            if (severity == null) {
                throw new IllegalStateException("severity is required");
            }
            return new PrevalidationFinding(this);
        }
    }
}
