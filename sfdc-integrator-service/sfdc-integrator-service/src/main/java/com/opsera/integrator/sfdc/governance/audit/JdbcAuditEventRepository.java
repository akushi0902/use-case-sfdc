package com.opsera.integrator.sfdc.governance.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * JDBC-backed implementation of {@link AuditEventRepository}.
 *
 * <p>Uses {@link JdbcTemplate} (auto-configured by spring-boot-starter-jdbc) for
 * INSERT-only access to the {@code audit_events} table. No UPDATE or DELETE
 * SQL is present in this class by design.
 *
 * <p>All persistence failures are wrapped in {@link AuditWriteException} with a
 * safe message that includes the event ID and operation but never credentials,
 * raw payloads, or stack trace content.
 */
@Repository
class JdbcAuditEventRepository implements AuditEventRepository {

    private static final String INSERT_SQL =
            "INSERT INTO audit_events " +
            "(event_id, correlation_id, actor_type, actor_ref, resource_type, resource_ref, " +
            " operation, classification, safe_metadata, event_timestamp, request_source) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    JdbcAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(AuditEvent event) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getActorType().name(),
                    event.getActorRef(),
                    event.getResourceType().name(),
                    event.getResourceRef(),
                    event.getOperation().name(),
                    event.getClassification().name(),
                    event.getSafeMetadata(),
                    event.getEventTimestamp() != null
                            ? Timestamp.from(event.getEventTimestamp())
                            : new Timestamp(System.currentTimeMillis()),
                    event.getRequestSource());
        } catch (Exception ex) {
            throw new AuditWriteException(
                    event.getEventId(),
                    event.getOperation(),
                    "JDBC_INSERT_FAILED",
                    ex);
        }
    }
}
