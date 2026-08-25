package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@link PostRefreshTaskService}. Classification context is resolved at request entry
 * for governance and structured logging (AC-2, AC-3).
 */
@RestController
public class PostRefreshTaskController {

    private static final Logger log = LoggerFactory.getLogger(PostRefreshTaskController.class);

    static final String SUCCESS = "SUCCESS";

    private final PostRefreshTaskService postRefreshTaskService;
    private final ClassificationPolicyResolver classificationResolver;

    public PostRefreshTaskController(PostRefreshTaskService postRefreshTaskService,
                                      ClassificationPolicyResolver classificationResolver) {
        this.postRefreshTaskService = postRefreshTaskService;
        this.classificationResolver = classificationResolver;
    }

    /**
     * Executes a post-refresh task.
     * Logs structured classification context only — not the raw request body.
     */
    @PostMapping("/postrefresh")
    public ResponseEntity<String> postRefresh(@RequestBody PostRefreshRequest request) {
        ClassificationContext ctx = classificationResolver.resolve(
                GovernanceDataCategory.JOB_METADATA, "post-refresh");
        if (ctx != null) {
            log.debug("Post-refresh task received: operation={}, classification={}",
                    ctx.getOperationName(), ctx.getClassification());
        }
        postRefreshTaskService.execute(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
