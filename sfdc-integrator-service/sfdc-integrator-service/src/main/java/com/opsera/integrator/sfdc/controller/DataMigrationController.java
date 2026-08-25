package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.audit.SafeAuditMetadata;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
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
 * <p>Emits a {@link AuditOperation#DATA_MIGRATION_INITIATED} audit event on every
 * accepted request. Audit write failures propagate — governed mutations must not
 * proceed without an audit record.
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

    public DataMigrationController(DataMigrationService dataMigrationService,
                                    ClassificationPolicyResolver classificationResolver,
                                    SafeStructuredLogger safeLogger,
                                    AuditEventWriter auditWriter) {
        this.dataMigrationService = dataMigrationService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
        this.auditWriter = auditWriter;
    }

    /**
     * Initiates a Salesforce data migration.
     * Logs allow-listed fields only — org URLs and raw request content are not logged.
     * Emits a DATA_MIGRATION_INITIATED audit event with pipelineId and stepId only.
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
