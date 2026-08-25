package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
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
 * <p>Returns a plain {@code "SUCCESS"} acknowledgement on HTTP 200 after delegating to
 * {@link DataMigrationService}.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request content, org URLs,
 * and DTO toString output are never emitted to logs (AC-1, AC-3).
 */
@RestController
public class DataMigrationController {

    static final String SUCCESS = "SUCCESS";

    private final DataMigrationService dataMigrationService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;

    public DataMigrationController(DataMigrationService dataMigrationService,
                                    ClassificationPolicyResolver classificationResolver,
                                    SafeStructuredLogger safeLogger) {
        this.dataMigrationService = dataMigrationService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
    }

    /**
     * Initiates a Salesforce data migration.
     * Logs allow-listed fields only — org URLs and raw request content are not logged.
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
        return ResponseEntity.ok(SUCCESS);
    }
}
