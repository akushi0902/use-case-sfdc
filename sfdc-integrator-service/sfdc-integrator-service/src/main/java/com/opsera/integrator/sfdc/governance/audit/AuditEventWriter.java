package com.opsera.integrator.sfdc.governance.audit;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service that constructs and persists immutable audit events for governed mutations.
 *
 * <p>Callers provide the operation-specific fields (actorRef, resourceType, resourceRef,
 * safeMetadata); this service resolves the correlationId from MDC, assigns a UUID
 * event ID, and sets the event timestamp before delegating to the repository.
 *
 * <p>When {@code sfdc.audit.enabled} is {@code false} the write is skipped and a
 * debug log is emitted — this allows local development without a database. In
 * production and CI environments the property should always be {@code true}.
 *
 * <p>Write failures throw {@link AuditWriteException} — callers at governed mutation
 * boundaries must NOT catch this exception and silently continue.
 */
@Service
public class AuditEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditEventWriter.class);

    private final AuditEventRepository repository;
    private final boolean auditEnabled;

    public AuditEventWriter(AuditEventRepository repository,
                             @Value("${sfdc.audit.enabled:true}") boolean auditEnabled) {
        this.repository = repository;
        this.auditEnabled = auditEnabled;
    }

    /**
     * Builds and persists an audit event for a governed mutation boundary.
     *
     * @param actorRef     opaque actor reference (e.g. pipelineId) — no raw PII
     * @param resourceType the type of resource acted upon
     * @param resourceRef  opaque resource reference (e.g. pipelineId or jobId)
     * @param operation    the governed operation being audited
     * @param classification data classification tier for this event
     * @param safeMetadata serialized safe metadata JSON (from {@link SafeAuditMetadata.Builder})
     * @param requestSource the controller method that triggered this event
     * @throws AuditWriteException if the event cannot be persisted and audit is enabled
     */
    public void write(String actorRef,
                      AuditResourceType resourceType,
                      String resourceRef,
                      AuditOperation operation,
                      DataClassification classification,
                      String safeMetadata,
                      String requestSource) {

        if (!auditEnabled) {
            log.debug("Audit disabled — skipping audit event: operation={} resourceType={}",
                      operation, resourceType);
            return;
        }

        String eventId = UUID.randomUUID().toString();
        String correlationId = resolveCorrelationId();

        AuditEvent event = AuditEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .actorType(AuditActorType.APPLICATION)
                .actorRef(actorRef != null && !actorRef.isBlank() ? actorRef : correlationId)
                .resourceType(resourceType)
                .resourceRef(resourceRef != null && !resourceRef.isBlank() ? resourceRef : "unknown")
                .operation(operation)
                .classification(classification != null ? classification : DataClassification.CONFIDENTIAL)
                .safeMetadata(safeMetadata)
                .eventTimestamp(Instant.now())
                .requestSource(requestSource)
                .build();

        try {
            repository.append(event);
            log.debug("Audit event persisted: eventId={} operation={} classification={}",
                      eventId, operation, classification);
        } catch (AuditWriteException ex) {
            log.error("Audit write failed: eventId={} operation={} category={} — governed " +
                      "mutations must not proceed without audit",
                      ex.getEventId(), ex.getOperation(), ex.getFailureCategory());
            throw ex;
        }
    }

    private String resolveCorrelationId() {
        String fromMdc = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString();
    }
}
