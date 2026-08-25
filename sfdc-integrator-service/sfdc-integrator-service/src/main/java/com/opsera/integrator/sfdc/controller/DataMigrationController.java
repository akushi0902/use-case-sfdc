package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.audit.SafeAuditMetadata;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.retention.RetentionMetadataService;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DataMigrationRequest;
import com.opsera.integrator.sfdc.service.DataMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy REST controller for Salesforce data migration operations.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /datamigration — initiate a Salesforce data migration</li>
 * </ul>
 *
 * <p>Emits a {@link AuditOperation#DATA_MIGRATION_INITIATED} audit event and assigns
 * retention metadata on every accepted request. Both write failures propagate —
 * governed mutations must not proceed without an audit record or retention metadata.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request content, org URLs,
 * and DTO toString output are never emitted to logs.
 */
@RestController
public class DataMigrationController {

    static final String SUCCESS = "SUCCESS";

    private final DataMigrationService dataMigrationService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;
    private final AuditEventWriter auditWriter;
    private final RetentionMetadataService retentionMetadataService;

    public DataMigrationController(DataMigrationService dataMigrationService,
                                    ClassificationPolicyResolver classificationResolver,
                                    SafeStructuredLogger safeLogger,
                                    AuditEventWriter auditWriter,
                                    RetentionMetadataService retentionMetadataService) {
        this.dataMigrationService = dataMigrationService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
        this.auditWriter = auditWriter;
        this.retentionMetadataService = retentionMetadataService;
    }

    /**
     * Initiates a Salesforce data migration.
     * Logs allow-listed fields only — org URLs and raw request content are not logged.
     * Emits a DATA_MIGRATION_INITIATED audit event and assigns SFDC_ARTIFACT retention metadata
     * with pipelineId and stepId only.
     */
    @PostMapping("/datamigration")
    public ResponseEntity<String> migrate(@RequestBody DataMigrationRequest request) {
        classificationResolver.resolve(GovernanceDataCategory.SFDC_ARTIFACT, "data-migration");

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("data-migration")
                .controller("DataMigrationController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        retentionMetadataService.assign(
                request.getPipelineId(),
                request.getStepId(),
                GovernanceDataCategory.SFDC_ARTIFACT,
                "data-migration");

        dataMigrationService.migrate(request);

        auditWriter.write(
                request.getPipelineId(),
                AuditResourceType.DATA_MIGRATION,
                request.getPipelineId() != null ? request.getPipelineId() : "unknown",
                AuditOperation.DATA_MIGRATION_INITIATED,
                DataClassification.RESTRICTED,
                SafeAuditMetadata.builder()
                        .field("pipelineId", request.getPipelineId())
                        .field("stepId", request.getStepId())
                        .field("outcome", "INITIATED")
                        .toJson(),
                "DataMigrationController.migrate");

        return ResponseEntity.ok(SUCCESS);
    }
}
