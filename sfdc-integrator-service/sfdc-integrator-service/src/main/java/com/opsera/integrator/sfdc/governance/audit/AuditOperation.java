package com.opsera.integrator.sfdc.governance.audit;

/**
 * Governed operation types that produce an immutable audit event.
 *
 * <p>Each value corresponds to a named mutation boundary in the service.
 * Only operations listed here can generate audit records — unknown or
 * uncategorised operations must not be written to the audit store.
 */
public enum AuditOperation {

    /** A new quick deploy or V2 release job was accepted and queued. */
    JOB_SUBMITTED,

    /** A running quick deploy job received a cancellation request. */
    JOB_CANCELLED,

    /** A lifecycle state transition was applied to a durable job record. */
    JOB_LIFECYCLE_TRANSITION,

    /** A Salesforce data migration operation was initiated. */
    DATA_MIGRATION_INITIATED,

    /** A post-refresh task was initiated (org refresh follow-up). */
    POST_REFRESH_INITIATED,

    /**
     * A data purge was executed on one or more lifecycle records.
     * Requires elevated classification and explicit break-glass approval.
     */
    PURGE_ACTION,

    /**
     * A privacy action was executed (e.g. GDPR/CCPA data subject request).
     * Requires RESTRICTED classification and compliance sign-off.
     */
    PRIVACY_ACTION,

    /** A V2 release command was accepted through the command facade. */
    RELEASE_COMMAND_ACCEPTED
}
