package com.opsera.integrator.sfdc.governance.classification;

/**
 * Tier-based data classification for job lifecycle metadata, generated artifacts,
 * and operational data. Ordered from least to most sensitive.
 *
 * <p>Classification is assigned by {@link ClassificationPolicyResolver} and must be
 * deterministic: the same category always maps to the same tier for a given configuration.
 * Unknown categories fail closed to {@link #CONFIDENTIAL}.
 */
public enum DataClassification {

    /**
     * Non-sensitive data safe for unrestricted internal logging and diagnostics.
     * Example: service health metrics, anonymous aggregates.
     */
    PUBLIC,

    /**
     * Internal service metadata logged with safe structured fields only.
     * Example: operational log entries, cache hit/miss counters, queue depths.
     */
    INTERNAL,

    /**
     * Job context, pipeline identifiers, and customer references.
     * Log classification tier and safe identifiers only — never raw payload.
     * Example: job lifecycle events, checkpoint state, audit context.
     */
    CONFIDENTIAL,

    /**
     * Salesforce credentials, org tokens, generated deployment artifacts, and
     * report contents. Must never appear in logs, diagnostics, or responses.
     * Example: org authentication tokens, generated change sets, CLI output.
     */
    RESTRICTED
}
