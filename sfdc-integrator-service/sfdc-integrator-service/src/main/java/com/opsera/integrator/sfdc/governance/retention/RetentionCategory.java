package com.opsera.integrator.sfdc.governance.retention;

/**
 * Authoritative retention category for job and artifact records.
 *
 * <p>Maps directly from {@link com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint}
 * and drives the duration lookup in {@link RetentionPolicyProperties}.
 *
 * <p>Categories are ordered from shortest to longest retention window:
 * SHORT_TERM → STANDARD → EXTENDED → AUDIT_MANDATED.
 */
public enum RetentionCategory {

    /** Short operational window — transient cache entries, debug logs. */
    SHORT_TERM,

    /** Standard business retention — job metadata, lifecycle checkpoints. */
    STANDARD,

    /** Extended compliance window — Salesforce artifacts, audit context. */
    EXTENDED,

    /** Legally mandated retention — GDPR/CCPA audit records. */
    AUDIT_MANDATED
}
