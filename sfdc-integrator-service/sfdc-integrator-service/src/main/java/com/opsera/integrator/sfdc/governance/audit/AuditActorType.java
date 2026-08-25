package com.opsera.integrator.sfdc.governance.audit;

/**
 * Classifies the type of actor that initiated an auditable operation.
 *
 * <p>Used in {@link AuditEvent#getActorType()} to distinguish automated pipeline
 * invocations from human operator actions and internal system processes.
 */
public enum AuditActorType {

    /** An upstream CI/CD pipeline or orchestration system invoked the operation. */
    APPLICATION,

    /** An authenticated human operator performed the action directly. */
    OPERATOR,

    /** An internal scheduled process or background worker initiated the action. */
    SYSTEM
}
