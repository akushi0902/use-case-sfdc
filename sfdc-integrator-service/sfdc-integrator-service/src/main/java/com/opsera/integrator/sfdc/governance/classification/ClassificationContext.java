package com.opsera.integrator.sfdc.governance.classification;

/**
 * Immutable classification context produced by {@link ClassificationPolicyResolver}.
 *
 * <p>Safe for structured logging: contains only classification metadata, never raw payload
 * content or credential fields. Callers must log only the fields exposed by this class.
 */
public class ClassificationContext {

    private final GovernanceDataCategory category;
    private final DataClassification classification;
    private final RetentionCategoryHint retentionHint;
    private final String operationName;

    public ClassificationContext(GovernanceDataCategory category,
                                  DataClassification classification,
                                  RetentionCategoryHint retentionHint,
                                  String operationName) {
        this.category = category;
        this.classification = classification;
        this.retentionHint = retentionHint;
        this.operationName = operationName != null ? operationName : "unknown";
    }

    public GovernanceDataCategory getCategory() { return category; }
    public DataClassification getClassification() { return classification; }
    public RetentionCategoryHint getRetentionHint() { return retentionHint; }
    public String getOperationName() { return operationName; }

    /**
     * Safe toString: includes only classification metadata, no payload or credential content.
     */
    @Override
    public String toString() {
        return "ClassificationContext{category=" + category +
                ", classification=" + classification +
                ", retentionHint=" + retentionHint +
                ", operation='" + operationName + '\'' + '}';
    }
}
