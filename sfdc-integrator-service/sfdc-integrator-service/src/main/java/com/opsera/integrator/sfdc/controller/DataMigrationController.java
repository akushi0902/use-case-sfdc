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
import com.opsera.integrator.sfdc.service.DataDictionaryService;
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
 *   <li>POST /customsettings — migrate custom settings</li>
 *   <li>POST /list/fieldproperties — list field properties for migration objects</li>
 *   <li>POST /validate/soql — validate SOQL queries before migration</li>
 *   <li>POST /org/limits — retrieve org limits relevant to migration</li>
 *   <li>POST /soql/details — retrieve SOQL query details</li>
 *   <li>POST /dependent-sobjects — list dependent sObject relationships</li>
 *   <li>POST /sobjects — list sObjects available for migration</li>
 *   <li>POST /managedpackage-list — list managed packages in the source org</li>
 *   <li>POST /list/filterableFields — list filterable fields for SOQL generation</li>
 *   <li>POST /validate/migration-entities — validate entities selected for migration</li>
 *   <li>POST /generate/data-dictionary — initiate data dictionary generation</li>
 * </ul>
 *
 * <p>The governed POST /datamigration route emits a {@link AuditOperation#DATA_MIGRATION_INITIATED}
 * audit event and assigns retention metadata. All other routes are legacy delegation stubs
 * returning plain success or accepted acknowledgements.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request content, org URLs,
 * and DTO toString output are never emitted to logs.
 */
@RestController
public class DataMigrationController {

    static final String SUCCESS = "SUCCESS";
    static final String ACCEPTED = "ACCEPTED";

    private final DataMigrationService dataMigrationService;
    private final DataDictionaryService dataDictionaryService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;
    private final AuditEventWriter auditWriter;
    private final RetentionMetadataService retentionMetadataService;

    public DataMigrationController(DataMigrationService dataMigrationService,
                                    DataDictionaryService dataDictionaryService,
                                    ClassificationPolicyResolver classificationResolver,
                                    SafeStructuredLogger safeLogger,
                                    AuditEventWriter auditWriter,
                                    RetentionMetadataService retentionMetadataService) {
        this.dataMigrationService = dataMigrationService;
        this.dataDictionaryService = dataDictionaryService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
        this.auditWriter = auditWriter;
        this.retentionMetadataService = retentionMetadataService;
    }

    /**
     * Initiates a Salesforce data migration.
     * Logs allow-listed fields only — org URLs and raw request content are not logged.
     * Emits a DATA_MIGRATION_INITIATED audit event and assigns SFDC_ARTIFACT retention metadata.
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

    /** Migrates custom settings from source to target org. */
    @PostMapping("/customsettings")
    public ResponseEntity<String> customSettings(@RequestBody DataMigrationRequest request) {
        dataMigrationService.listCustomSettings(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists field properties for objects selected for migration. */
    @PostMapping("/list/fieldproperties")
    public ResponseEntity<String> listFieldProperties(@RequestBody DataMigrationRequest request) {
        dataMigrationService.listFieldProperties(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Validates SOQL queries before executing data migration. */
    @PostMapping("/validate/soql")
    public ResponseEntity<String> validateSoql(@RequestBody DataMigrationRequest request) {
        dataMigrationService.validateSoql(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Retrieves org limits relevant to the planned migration. */
    @PostMapping("/org/limits")
    public ResponseEntity<String> orgLimits(@RequestBody DataMigrationRequest request) {
        dataMigrationService.getOrgLimits(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Retrieves details for a given SOQL query. */
    @PostMapping("/soql/details")
    public ResponseEntity<String> soqlDetails(@RequestBody DataMigrationRequest request) {
        dataMigrationService.getSoqlDetails(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists dependent sObject relationships for migration planning. */
    @PostMapping("/dependent-sobjects")
    public ResponseEntity<String> dependentSObjects(@RequestBody DataMigrationRequest request) {
        dataMigrationService.getDependentSObjects(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists sObjects available for migration in the source org. */
    @PostMapping("/sobjects")
    public ResponseEntity<String> sObjects(@RequestBody DataMigrationRequest request) {
        dataMigrationService.listSObjects(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists managed packages installed in the source org. */
    @PostMapping("/managedpackage-list")
    public ResponseEntity<String> managedPackageList(@RequestBody DataMigrationRequest request) {
        dataMigrationService.listManagedPackages(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists filterable fields available for SOQL generation. */
    @PostMapping("/list/filterableFields")
    public ResponseEntity<String> listFilterableFields(@RequestBody DataMigrationRequest request) {
        dataMigrationService.listFilterableFields(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Validates entities selected for migration.
     * Returns ACCEPTED as a checkpoint-style acknowledgement — downstream
     * validation proceeds asynchronously after initial entity checks.
     */
    @PostMapping("/validate/migration-entities")
    public ResponseEntity<String> validateMigrationEntities(@RequestBody DataMigrationRequest request) {
        dataMigrationService.validateMigrationEntities(request);
        return ResponseEntity.ok(ACCEPTED);
    }

    /**
     * Initiates data dictionary generation for migration metadata.
     * Returns ACCEPTED as a checkpoint-style acknowledgement — dictionary
     * generation is a long-running operation deferred to the service layer.
     */
    @PostMapping("/generate/data-dictionary")
    public ResponseEntity<String> generateDataDictionary(@RequestBody DataMigrationRequest request) {
        dataDictionaryService.generateDataDictionary(request);
        return ResponseEntity.ok(ACCEPTED);
    }
}
