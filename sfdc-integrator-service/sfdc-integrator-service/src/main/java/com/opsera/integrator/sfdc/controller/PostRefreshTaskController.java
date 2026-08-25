package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DomainQueueRequest;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Legacy REST controller for Salesforce post-refresh task operations.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /postrefresh              — execute a post-refresh task pipeline step</li>
 *   <li>POST /apex/schedulerclasses   — retrieve Apex scheduler classes (ACCEPTED)</li>
 *   <li>POST /domainqueue/task/remove — remove a task from the domain queue</li>
 *   <li>GET  /domainqueue/task/list   — list tasks in the domain queue</li>
 *   <li>POST /domainmap/clear         — clear the domain map</li>
 * </ul>
 *
 * <p>Returns plain {@code "SUCCESS"} acknowledgements on HTTP 200 for synchronous paths.
 * Operational logging uses {@link SafeStructuredLogger} — raw request bodies and DTO
 * toString output are never emitted to logs.
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

    /** Retrieves Apex scheduler classes for the given pipeline step. Returns SUCCESS acknowledgement. */
    @PostMapping("/apex/schedulerclasses")
    public ResponseEntity<String> getSchedulerClasses(@RequestBody PostRefreshRequest request) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("apex-scheduler-classes")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        postRefreshTaskService.retrieveSchedulerClasses(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Removes a task from the domain queue. Returns SUCCESS acknowledgement. */
    @PostMapping("/domainqueue/task/remove")
    public ResponseEntity<String> removeDomainQueueTask(@RequestBody DomainQueueRequest request) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("domain-queue-remove")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        postRefreshTaskService.removeDomainQueueTask(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /** Lists tasks in the domain queue for the given pipeline step. Always returns HTTP 200. */
    @GetMapping("/domainqueue/task/list")
    public ResponseEntity<List<String>> listDomainQueueTasks(
            @RequestParam String pipelineId,
            @RequestParam String stepId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("domain-queue-list")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", pipelineId)
                .safeField("stepId", stepId)
                .build());

        return ResponseEntity.ok(postRefreshTaskService.listDomainQueueTasks(pipelineId, stepId));
    }

    /** Clears the domain map for the given pipeline step. Returns SUCCESS acknowledgement. */
    @PostMapping("/domainmap/clear")
    public ResponseEntity<String> clearDomainMap(@RequestBody DomainQueueRequest request) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("domain-map-clear")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        postRefreshTaskService.clearDomainMap(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
