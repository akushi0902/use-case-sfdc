package com.opsera.integrator.sfdc.controller;

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

    public SfdcIntegratorController(SfdcIntegratorService sfdcIntegratorService) {
        this.sfdcIntegratorService = sfdcIntegratorService;
    }

    /**
     * Deploys Salesforce metadata to the target org.
     */
    @PostMapping("/deploy")
    public ResponseEntity<String> deploy(@RequestBody DeployRequest request) {
        log.debug("Deploy request received for pipeline={}", request.getPipelineId());
        sfdcIntegratorService.deploy(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Validates Salesforce metadata for the target org without committing a deployment.
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validate(@RequestBody DeployRequest request) {
        log.debug("Validate request received for pipeline={}", request.getPipelineId());
        sfdcIntegratorService.validate(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
