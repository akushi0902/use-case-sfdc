package com.opsera.integrator.sfdc.governance.classification;

/**
 * Retention category hint for governed data.
 *
 * <p>These are advisory hints for storage, purge, and archival subsystems.
 * Actual retention periods are defined in organizational policy, not here.
 * This enum provides a stable symbolic reference that later retention and
 * purge stories can consume without duplicating the category-to-period logic.
 */
public enum RetentionCategoryHint {

    /** Short operational window (hours to days). Example: transient cache entries, debug logs. */
    SHORT_TERM,

    /** Standard business retention window (weeks to one year). Example: job metadata, checkpoints. */
    STANDARD,

    /** Extended compliance window (multi-year). Example: Salesforce artifacts, audit context. */
    EXTENDED,

    /** Retention governed by a legal hold or regulatory mandate. Example: GDPR/CCPA audit records. */
    AUDIT_MANDATED
}
