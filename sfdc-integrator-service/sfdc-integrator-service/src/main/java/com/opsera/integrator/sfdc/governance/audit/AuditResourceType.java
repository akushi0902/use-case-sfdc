package com.opsera.integrator.sfdc.governance.audit;

/**
 * Logical resource types that can be the subject of an auditable operation.
 *
 * <p>Used in {@link AuditEvent#getResourceType()} to identify what resource
 * was acted upon in a given audit event row.
 */
public enum AuditResourceType {

    /** A legacy quick deploy job managed via {@code /quickdeploy} routes. */
    QUICK_DEPLOY_JOB,

    /** A durable lifecycle job record from the V2 job schema. */
    LIFECYCLE_JOB,

    /** A Salesforce data migration operation. */
    DATA_MIGRATION,

    /** A V2 release command submitted via {@code /api/v2/sfdc/release-jobs}. */
    RELEASE_JOB,

    /** A post-refresh operation on an org after a sandbox refresh. */
    POST_REFRESH_TASK
}
