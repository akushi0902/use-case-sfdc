package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import jakarta.validation.Valid;
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
 * <p>Input validation on {@code startQuickDeploy} is enforced via Jakarta Bean Validation
 * ({@code @Valid}); invalid requests throw {@code MethodArgumentNotValidException} which
 * is mapped to a structured 400 response by {@code SfdcExceptionHandler}.
 */
@RestController
@RequestMapping("/quickdeploy")
public class JobExecutionController {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionController.class);

    static final String SUCCESS = "SUCCESS";

    private final QuickDeployService quickDeployService;
    private final ClassificationPolicyResolver classificationResolver;

    public JobExecutionController(QuickDeployService quickDeployService,
                                   ClassificationPolicyResolver classificationResolver) {
        this.quickDeployService = quickDeployService;
        this.classificationResolver = classificationResolver;
    }

    /**
     * Starts a quick deploy job.
     *
     * <p>Jakarta Bean Validation rejects requests where {@code deploymentRequestId}
     * is absent or blank before this method is invoked.
     * Normalizes a null {@code fallbackTaskId} to an empty string before delegation.
     */
    @PostMapping
    public ResponseEntity<String> startQuickDeploy(@Valid @RequestBody QuickDeployRequest request) {
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.JOB_METADATA, "quick-deploy-start");
        if (ctx != null) {
            log.debug("Quick deploy start received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
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
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.JOB_METADATA, "quick-deploy-stop");
        if (ctx != null) {
            log.debug("Quick deploy stop received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
        }
        quickDeployService.stop(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
