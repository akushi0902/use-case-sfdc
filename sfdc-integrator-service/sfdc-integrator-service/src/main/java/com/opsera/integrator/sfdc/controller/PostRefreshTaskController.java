package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy REST controller for Salesforce post-refresh task operations.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /postrefresh — execute a post-refresh task pipeline step</li>
 * </ul>
 *
 * <p>Returns a plain {@code "SUCCESS"} acknowledgement on HTTP 200 after delegating to
 * {@link PostRefreshTaskService}.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request bodies and DTO
 * toString output are never emitted to logs (AC-1, AC-3).
 */
@RestController
public class PostRefreshTaskController {

    static final String SUCCESS = "SUCCESS";

    private final PostRefreshTaskService postRefreshTaskService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;

    public PostRefreshTaskController(PostRefreshTaskService postRefreshTaskService,
                                      ClassificationPolicyResolver classificationResolver,
                                      SafeStructuredLogger safeLogger) {
        this.postRefreshTaskService = postRefreshTaskService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
    }

    /**
     * Executes a post-refresh task.
     * Logs allow-listed fields only — raw request content is not logged.
     */
    @PostMapping("/postrefresh")
    public ResponseEntity<String> postRefresh(@RequestBody PostRefreshRequest request) {
        classificationResolver.resolve(GovernanceDataCategory.JOB_METADATA, "post-refresh");

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("post-refresh")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        postRefreshTaskService.execute(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
