package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy REST controller for quick deploy job execution operations.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /quickdeploy — start a quick deploy job</li>
 *   <li>POST /quickdeploy/stop — cancel a running quick deploy job</li>
 * </ul>
 *
 * <p>Both endpoints return a plain {@code "SUCCESS"} acknowledgement string on HTTP 200
 * after delegating to the {@link QuickDeployService}. Long-running Salesforce execution
 * proceeds asynchronously after acknowledgement.
 *
 * <p>Note: input validation is performed explicitly inside the handler methods rather
 * than through {@code @Valid} annotations, matching current legacy behavior.
 */
@RestController
@RequestMapping("/quickdeploy")
public class JobExecutionController {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionController.class);

    static final String SUCCESS = "SUCCESS";
    static final String ERR_MISSING_DEPLOYMENT_ID = "deploymentRequestId is required";

    private final QuickDeployService quickDeployService;

    public JobExecutionController(QuickDeployService quickDeployService) {
        this.quickDeployService = quickDeployService;
    }

    /**
     * Starts a quick deploy job.
     *
     * <p>Rejects requests where {@code deploymentRequestId} is absent or blank.
     * Normalizes a null {@code fallbackTaskId} to an empty string before delegation.
     */
    @PostMapping
    public ResponseEntity<String> startQuickDeploy(@RequestBody QuickDeployRequest request) {
        log.debug("Quick deploy start received for pipeline={}", request.getPipelineId());

        if (request.getDeploymentRequestId() == null
                || request.getDeploymentRequestId().isBlank()) {
            log.warn("Quick deploy start rejected: deploymentRequestId is missing");
            return ResponseEntity.badRequest().body(ERR_MISSING_DEPLOYMENT_ID);
        }

        // Normalize optional fallback field so downstream code never sees null
        if (request.getFallbackTaskId() == null) {
            request.setFallbackTaskId("");
        }

        quickDeployService.start(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Cancels a running quick deploy job.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopQuickDeploy(@RequestBody QuickDeployStopRequest request) {
        log.debug("Quick deploy stop received for pipeline={}", request.getPipelineId());
        quickDeployService.stop(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
