package com.opsera.integrator.sfdc.governance.audit;

/**
 * Append-only persistence contract for audit events.
 *
 * <p>This interface intentionally exposes only an {@link #append(AuditEvent)} method.
 * No update, delete, patch, or overwrite method exists — removing such a method from
 * this interface is a breaking change that requires compliance sign-off.
 *
 * <p>Database-level enforcement complements the interface contract:
 * see {@code V3__audit_events_schema.sql} for the break-glass procedure and the
 * {@code REVOKE UPDATE, DELETE} administrative step.
 */
public interface AuditEventRepository {

    /**
     * Persists an audit event. The event is written exactly once; retries
     * with the same {@code event_id} must be idempotent at the caller level
     * or produce a distinct audit record with a new event ID.
     *
     * @param event the audit event to append; must not be {@code null}
     * @throws AuditWriteException if the event cannot be persisted
     */
    void append(AuditEvent event);
}
