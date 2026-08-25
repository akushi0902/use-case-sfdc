package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy REST controller for Salesforce integration operations including
 * metadata deployment and validation.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /deploy — deploy Salesforce metadata to an org</li>
 *   <li>POST /validate — validate pending Salesforce metadata without committing</li>
 * </ul>
 *
 * <p>Both endpoints return a plain {@code "SUCCESS"} acknowledgement on HTTP 200
 * after delegating to {@link SfdcIntegratorService}.
 */
@RestController
public class SfdcIntegratorController {

    private static final Logger log = LoggerFactory.getLogger(SfdcIntegratorController.class);

    static final String SUCCESS = "SUCCESS";

    private final SfdcIntegratorService sfdcIntegratorService;
    private final ClassificationPolicyResolver classificationResolver;

    public SfdcIntegratorController(SfdcIntegratorService sfdcIntegratorService,
                                     ClassificationPolicyResolver classificationResolver) {
        this.sfdcIntegratorService = sfdcIntegratorService;
        this.classificationResolver = classificationResolver;
    }

    /**
     * Deploys Salesforce metadata to the target org.
     * Logs classification context only — not the raw request body.
     */
    @PostMapping("/deploy")
    public ResponseEntity<String> deploy(@RequestBody DeployRequest request) {
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.SFDC_ARTIFACT, "deploy");
        if (ctx != null) {
            log.debug("Deploy request received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
        }
        sfdcIntegratorService.deploy(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Validates Salesforce metadata for the target org without committing a deployment.
     * Logs classification context only — not the raw request body.
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validate(@RequestBody DeployRequest request) {
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.SFDC_ARTIFACT, "validate");
        if (ctx != null) {
            log.debug("Validate request received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
        }
        sfdcIntegratorService.validate(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
