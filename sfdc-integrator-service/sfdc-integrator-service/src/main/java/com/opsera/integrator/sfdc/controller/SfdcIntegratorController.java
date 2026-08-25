package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
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
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request bodies,
 * org URLs, and DTO toString output are never emitted to logs.
 */
@RestController
public class SfdcIntegratorController {

    static final String SUCCESS = "SUCCESS";

    private final SfdcIntegratorService sfdcIntegratorService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;

    public SfdcIntegratorController(SfdcIntegratorService sfdcIntegratorService,
                                     ClassificationPolicyResolver classificationResolver,
                                     SafeStructuredLogger safeLogger) {
        this.sfdcIntegratorService = sfdcIntegratorService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
    }

    /**
     * Deploys Salesforce metadata to the target org.
     * Logs allow-listed fields only — raw request content and org URLs are not logged.
     */
    @PostMapping("/deploy")
    public ResponseEntity<String> deploy(@RequestBody DeployRequest request) {
        classificationResolver.resolve(GovernanceDataCategory.SFDC_ARTIFACT, "deploy");

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        sfdcIntegratorService.deploy(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Validates Salesforce metadata for the target org without committing a deployment.
     * Logs allow-listed fields only — raw request content and org URLs are not logged.
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validate(@RequestBody DeployRequest request) {
        classificationResolver.resolve(GovernanceDataCategory.SFDC_ARTIFACT, "validate");

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("validate")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        sfdcIntegratorService.validate(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
