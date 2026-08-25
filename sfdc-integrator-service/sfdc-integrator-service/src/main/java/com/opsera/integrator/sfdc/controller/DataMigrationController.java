package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.model.DataMigrationRequest;
import com.opsera.integrator.sfdc.service.DataMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@link DataMigrationService}. Classification context is resolved at request entry
 * for governance and structured logging (AC-2, AC-3).
 */
@RestController
public class DataMigrationController {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationController.class);

    static final String SUCCESS = "SUCCESS";

    private final DataMigrationService dataMigrationService;
    private final ClassificationPolicyResolver classificationResolver;

    public DataMigrationController(DataMigrationService dataMigrationService,
                                    ClassificationPolicyResolver classificationResolver) {
        this.dataMigrationService = dataMigrationService;
        this.classificationResolver = classificationResolver;
    }

    /**
     * Initiates a Salesforce data migration.
     * Logs structured classification context only — not the raw request body.
     */
    @PostMapping("/datamigration")
    public ResponseEntity<String> migrate(@RequestBody DataMigrationRequest request) {
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.SFDC_ARTIFACT, "data-migration");
        if (ctx != null) {
            log.debug("Data migration request received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
        }
        dataMigrationService.migrate(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
