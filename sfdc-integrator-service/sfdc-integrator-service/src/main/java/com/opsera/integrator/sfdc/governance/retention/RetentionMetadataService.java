package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.audit.SafeAuditMetadata;
import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Assigns and manages retention metadata for governed job and artifact records.
 *
 * <p>Every governed record creation path must call {@link #assign} before making the
 * record visible. If assignment fails, a {@link RetentionAssignmentException} is thrown
 * and the caller must abort the creation — ungoverned data must not be created.
 *
 * <p>Audit events are emitted for:
 * <ul>
 *   <li>{@link AuditOperation#RETENTION_METADATA_ASSIGNED} — on every successful assignment.</li>
 *   <li>{@link AuditOperation#LEGAL_HOLD_APPLIED} — when legal hold is activated or released.</li>
 * </ul>
 *
 * <p>Error messages include only correlation identifier and failure category — no raw
 * payload, org URL, credentials, or customer data.
 */
@Service
public class RetentionMetadataService {

    private static final Logger log = LoggerFactory.getLogger(RetentionMetadataService.class);

    private final RetentionPolicyResolver policyResolver;
    private final RetentionMetadataRepository repository;
    private final ClassificationPolicyResolver classificationResolver;
    private final AuditEventWriter auditWriter;

    public RetentionMetadataService(RetentionPolicyResolver policyResolver,
                                    RetentionMetadataRepository repository,
                                    ClassificationPolicyResolver classificationResolver,
                                    AuditEventWriter auditWriter) {
        this.policyResolver = policyResolver;
        this.repository = repository;
        this.classificationResolver = classificationResolver;
        this.auditWriter = auditWriter;
    }

    /**
     * Assigns retention metadata for a newly created governed record.
     *
     * <p>Resolves the classification and retention policy from the data category,
     * persists the metadata record, and emits a
     * {@link AuditOperation#RETENTION_METADATA_ASSIGNED} audit event.
     *
     * @param jobRef      opaque job reference (jobId or pipelineId) — no raw PII
     * @param artifactRef optional artifact reference (may be null)
     * @param category    governed data category
     * @param operationName safe operation label for log and audit context
     * @return the persisted {@link RetentionMetadata}
     * @throws RetentionAssignmentException if retention metadata cannot be assigned
     */
    public RetentionMetadata assign(String jobRef,
                                    String artifactRef,
                                    GovernanceDataCategory category,
                                    String operationName) {
        String correlationId = resolveCorrelationId();
        try {
            ClassificationContext ctx = classificationResolver.resolve(category, operationName);
            DataClassification classification = ctx.getClassification();

            RetentionMetadata metadata = policyResolver.buildMetadata(
                    jobRef, artifactRef, category, classification, ctx.getRetentionHint());

            repository.save(metadata);

            auditWriter.write(
                    jobRef,
                    AuditResourceType.RETENTION_RECORD,
                    jobRef != null ? jobRef : correlationId,
                    AuditOperation.RETENTION_METADATA_ASSIGNED,
                    classification,
                    SafeAuditMetadata.builder()
                            .field("retentionCategory", metadata.getRetentionCategory().name())
                            .field("purgeEligibilityStatus", metadata.getPurgeEligibilityStatus().name())
                            .field("legalHold", String.valueOf(metadata.isLegalHold()))
                            .field("outcome", "ASSIGNED")
                            .toJson(),
                    "RetentionMetadataService.assign");

            log.debug("Retention metadata assigned: jobRef={} category={} retentionCategory={}",
                      jobRef, category, metadata.getRetentionCategory());
            return metadata;

        } catch (RetentionAssignmentException ex) {
            log.error("Retention assignment failed: correlationId={} category={} — governed record must not be created",
                      correlationId, ex.getFailureCategory());
            throw ex;
        } catch (Exception ex) {
            log.error("Retention assignment error: correlationId={} category=PERSISTENCE_FAILURE",
                      correlationId);
            throw new RetentionAssignmentException(
                    "Retention metadata assignment failed for governed record",
                    correlationId,
                    "UNEXPECTED_FAILURE",
                    ex);
        }
    }

    /**
     * Applies or releases a legal hold on an existing retention metadata record.
     *
     * <p>When {@code legalHold} is true, purge eligibility is set to
     * {@link PurgeEligibilityStatus#LEGAL_HOLD} regardless of the retention expiration.
     * When released, eligibility is recomputed from the current clock.
     *
     * <p>Emits a {@link AuditOperation#LEGAL_HOLD_APPLIED} audit event.
     *
     * @param jobRef    opaque job reference
     * @param legalHold true to apply, false to release
     * @throws RetentionAssignmentException if the record does not exist or update fails
     */
    public void applyLegalHold(String jobRef, boolean legalHold) {
        String correlationId = resolveCorrelationId();
        try {
            RetentionMetadata existing = repository.findByJobRef(jobRef)
                    .orElseThrow(() -> new RetentionAssignmentException(
                            "Legal hold failed — no retention metadata found: jobRef=" + jobRef,
                            correlationId,
                            "RECORD_NOT_FOUND"));

            existing.setLegalHold(legalHold);
            PurgeEligibilityStatus newStatus = legalHold
                    ? PurgeEligibilityStatus.LEGAL_HOLD
                    : policyResolver.computeEligibility(existing);

            repository.updateLegalHold(jobRef, legalHold, newStatus);

            DataClassification classification = existing.getClassification() != null
                    ? existing.getClassification() : DataClassification.CONFIDENTIAL;

            auditWriter.write(
                    jobRef,
                    AuditResourceType.RETENTION_RECORD,
                    jobRef,
                    AuditOperation.LEGAL_HOLD_APPLIED,
                    classification,
                    SafeAuditMetadata.builder()
                            .field("legalHold", String.valueOf(legalHold))
                            .field("purgeEligibilityStatus", newStatus.name())
                            .field("outcome", legalHold ? "HOLD_APPLIED" : "HOLD_RELEASED")
                            .toJson(),
                    "RetentionMetadataService.applyLegalHold");

            log.debug("Legal hold updated: jobRef={} legalHold={} newStatus={}", jobRef, legalHold, newStatus);

        } catch (RetentionAssignmentException ex) {
            log.error("Legal hold update failed: correlationId={} category={}",
                      correlationId, ex.getFailureCategory());
            throw ex;
        }
    }

    private String resolveCorrelationId() {
        String fromMdc = MDC.get(CorrelationIdConstants.MDC_KEY);
        return (fromMdc != null && !fromMdc.isBlank()) ? fromMdc : "unknown";
    }
}
