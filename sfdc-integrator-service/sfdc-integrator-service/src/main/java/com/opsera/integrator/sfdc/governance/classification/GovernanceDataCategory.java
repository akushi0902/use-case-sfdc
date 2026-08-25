package com.opsera.integrator.sfdc.governance.classification;

/**
 * Governed data categories covering job lifecycle, generated artifacts, and operational data.
 * Used by {@link ClassificationPolicyResolver} to look up the configured
 * {@link DataClassification} tier and {@link RetentionCategoryHint}.
 */
public enum GovernanceDataCategory {

    /** Job start/stop/status events and metadata — pipeline ID, step ID, request ID. */
    JOB_METADATA,

    /** Intermediate checkpoints stored during a running job (progress, partial results). */
    LIFECYCLE_CHECKPOINT,

    /** Salesforce-generated deployment artifacts, change sets, and CLI output. */
    SFDC_ARTIFACT,

    /** Structured operational log entries from request handling and background threads. */
    OPERATIONAL_LOG,

    /** Audit trail entries written for compliance and data governance review. */
    AUDIT_CONTEXT,

    /** Short-lived cache entries for session state and cluster coordination. */
    CACHE_ENTRY
}
