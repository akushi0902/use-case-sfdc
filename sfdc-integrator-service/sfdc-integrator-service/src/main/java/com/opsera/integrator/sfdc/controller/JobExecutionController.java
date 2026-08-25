package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.audit.AuditOperation;
import com.opsera.integrator.sfdc.governance.audit.AuditResourceType;
import com.opsera.integrator.sfdc.governance.audit.SafeAuditMetadata;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.observability.SafeTraceAttributes;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.security.CallerContextResolver;
import com.opsera.integrator.sfdc.security.ScopeAuthorizer;
import com.opsera.integrator.sfdc.security.ScopeConstants;
import com.opsera.integrator.sfdc.security.ShellArgumentProfile;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * <p>Both endpoints emit an immutable audit event via {@link AuditEventWriter}
 * before returning. Audit write failures propagate as errors — governed mutations
 * must not proceed without an audit record.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request bodies and DTO
 * toString output are never emitted to logs.
 */
@RestController
@RequestMapping("/quickdeploy")
public class JobExecutionController {

    static final String SUCCESS = "SUCCESS";

    private final QuickDeployService quickDeployService;
    private final ClassificationPolicyResolver classificationResolver;
    private final SafeStructuredLogger safeLogger;
    private final AuditEventWriter auditWriter;
    private final ShellArgumentValidator shellArgumentValidator;
    private final CallerContextResolver callerContextResolver;
    private final ScopeAuthorizer scopeAuthorizer;
    private final TraceContextPropagation traceContextPropagation;
    private final ReleaseLifecycleMetrics releaseLifecycleMetrics;

    public JobExecutionController(QuickDeployService quickDeployService,
                                   ClassificationPolicyResolver classificationResolver,
                                   SafeStructuredLogger safeLogger,
                                   AuditEventWriter auditWriter,
                                   ShellArgumentValidator shellArgumentValidator,
                                   CallerContextResolver callerContextResolver,
                                   ScopeAuthorizer scopeAuthorizer,
                                   TraceContextPropagation traceContextPropagation,
                                   ReleaseLifecycleMetrics releaseLifecycleMetrics) {
        this.quickDeployService = quickDeployService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
        this.auditWriter = auditWriter;
        this.shellArgumentValidator = shellArgumentValidator;
        this.callerContextResolver = callerContextResolver;
        this.scopeAuthorizer = scopeAuthorizer;
        this.traceContextPropagation = traceContextPropagation;
        this.releaseLifecycleMetrics = releaseLifecycleMetrics;
    }

    /**
     * Starts a quick deploy job.
     *
     * <p>Emits a {@link AuditOperation#JOB_SUBMITTED} audit event with safe metadata
     * (pipelineId, stepId only). Audit write failures propagate to the exception handler.
     */
    @PostMapping
    public ResponseEntity<String> startQuickDeploy(@Valid @RequestBody QuickDeployRequest request,
                                                    Authentication authentication) {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        long startNanos = System.nanoTime();

        try (TraceContextPropagation.SpanInScope span =
                     traceContextPropagation.startSpan(SafeTraceAttributes.OP_QUICK_DEPLOY_START)) {
            span.tag(SafeTraceAttributes.ATTR_OPERATION, SafeTraceAttributes.OP_QUICK_DEPLOY_START)
                .tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_REQUEST_RECEIVED)
                .tag(SafeTraceAttributes.ATTR_HTTP_ROUTE, SafeTraceAttributes.ROUTE_QUICK_DEPLOY)
                .tag(SafeTraceAttributes.ATTR_CORRELATION_ID, correlationId != null ? correlationId : "");

            try {
                scopeAuthorizer.requireScope(
                        callerContextResolver.resolve(authentication),
                        ScopeConstants.QUICK_DEPLOY_SUBMIT,
                        "quick-deploy-start");

                classificationResolver.resolve(GovernanceDataCategory.JOB_METADATA, "quick-deploy-start");

                // Shell argument safety: validate all fields that may reach script execution before
                // delegating to the service. Unsafe values are rejected here; ShellArgumentViolationException
                // propagates to SfdcExceptionHandler which returns HTTP 400 with SHELL_UNSAFE_INPUT.
                shellArgumentValidator.validate(request.getDeploymentRequestId(), "deploymentRequestId",
                        ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
                shellArgumentValidator.validateIfPresent(request.getPipelineId(), "pipelineId",
                        ShellArgumentProfile.GENERIC_LABEL);
                shellArgumentValidator.validateIfPresent(request.getStepId(), "stepId",
                        ShellArgumentProfile.TASK_IDENTIFIER);
                shellArgumentValidator.validateIfPresent(request.getFallbackTaskId(), "fallbackTaskId",
                        ShellArgumentProfile.TASK_IDENTIFIER);

                span.tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_VALIDATION);

                safeLogger.logEvent(SafeLogEvent.builder()
                        .operation("quick-deploy-start")
                        .controller("JobExecutionController")
                        .outcome(SafeLogEvent.Outcome.ACCEPTED)
                        .safeField("pipelineId", request.getPipelineId())
                        .safeField("stepId", request.getStepId())
                        .build());

                // Normalize optional fallback field so downstream code never sees null
                if (request.getFallbackTaskId() == null) {
                    request.setFallbackTaskId("");
                }

                span.tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_DISPATCH);
                quickDeployService.start(request);

                auditWriter.write(
                        request.getPipelineId(),
                        AuditResourceType.QUICK_DEPLOY_JOB,
                        request.getPipelineId() != null ? request.getPipelineId() : "unknown",
                        AuditOperation.JOB_SUBMITTED,
                        DataClassification.CONFIDENTIAL,
                        SafeAuditMetadata.builder()
                                .field("pipelineId", request.getPipelineId())
                                .field("stepId", request.getStepId())
                                .field("outcome", "SUBMITTED")
                                .toJson(),
                        "JobExecutionController.startQuickDeploy");

                span.tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ACCEPTED);
                releaseLifecycleMetrics.recordAcceptanceAttempt(
                        ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED);
                releaseLifecycleMetrics.recordExecutionDuration(
                        System.nanoTime() - startNanos, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED);
                return ResponseEntity.ok(SUCCESS);
            } catch (Exception e) {
                span.tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_EXCEPTION)
                    .tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ERROR)
                    .error(e);
                releaseLifecycleMetrics.recordAcceptanceAttempt(
                        ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_REJECTED);
                releaseLifecycleMetrics.recordDispatchFailure(
                        ReleaseLifecycleMetrics.OP_QUICK_DEPLOY,
                        classifyException(e));
                throw e;
            }
        }
    }

    /**
     * Cancels a running quick deploy job.
     *
     * <p>Emits a {@link AuditOperation#JOB_CANCELLED} audit event with safe metadata
     * (pipelineId only). Audit write failures propagate to the exception handler.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopQuickDeploy(@RequestBody QuickDeployStopRequest request,
                                                   Authentication authentication) {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);

        try (TraceContextPropagation.SpanInScope span =
                     traceContextPropagation.startSpan(SafeTraceAttributes.OP_QUICK_DEPLOY_STOP)) {
            span.tag(SafeTraceAttributes.ATTR_OPERATION, SafeTraceAttributes.OP_QUICK_DEPLOY_STOP)
                .tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_CANCELLATION)
                .tag(SafeTraceAttributes.ATTR_HTTP_ROUTE, SafeTraceAttributes.ROUTE_QUICK_DEPLOY_STOP)
                .tag(SafeTraceAttributes.ATTR_CORRELATION_ID, correlationId != null ? correlationId : "");

            try {
                scopeAuthorizer.requireScope(
                        callerContextResolver.resolve(authentication),
                        ScopeConstants.QUICK_DEPLOY_CANCEL,
                        "quick-deploy-stop");

                classificationResolver.resolve(GovernanceDataCategory.JOB_METADATA, "quick-deploy-stop");

                // Shell argument safety: validate shell-bound fields before service delegation.
                shellArgumentValidator.validateIfPresent(request.getDeploymentRequestId(), "deploymentRequestId",
                        ShellArgumentProfile.DEPLOYMENT_IDENTIFIER);
                shellArgumentValidator.validateIfPresent(request.getPipelineId(), "pipelineId",
                        ShellArgumentProfile.GENERIC_LABEL);

                safeLogger.logEvent(SafeLogEvent.builder()
                        .operation("quick-deploy-stop")
                        .controller("JobExecutionController")
                        .outcome(SafeLogEvent.Outcome.ACCEPTED)
                        .safeField("pipelineId", request.getPipelineId())
                        .build());

                span.tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_DISPATCH);
                quickDeployService.stop(request);

                auditWriter.write(
                        request.getPipelineId(),
                        AuditResourceType.QUICK_DEPLOY_JOB,
                        request.getPipelineId() != null ? request.getPipelineId() : "unknown",
                        AuditOperation.JOB_CANCELLED,
                        DataClassification.CONFIDENTIAL,
                        SafeAuditMetadata.builder()
                                .field("pipelineId", request.getPipelineId())
                                .field("outcome", "CANCELLED")
                                .toJson(),
                        "JobExecutionController.stopQuickDeploy");

                span.tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ACCEPTED);
                releaseLifecycleMetrics.recordCancellationAttempt(ReleaseLifecycleMetrics.OUTCOME_CANCELLED);
                return ResponseEntity.ok(SUCCESS);
            } catch (Exception e) {
                span.tag(SafeTraceAttributes.ATTR_LIFECYCLE_PHASE, SafeTraceAttributes.PHASE_EXCEPTION)
                    .tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ERROR)
                    .error(e);
                releaseLifecycleMetrics.recordCancellationAttempt(ReleaseLifecycleMetrics.OUTCOME_FAILED);
                throw e;
            }
        }
    }

    private static String classifyException(Exception e) {
        if (e == null) {
            return ReleaseLifecycleMetrics.EX_CATEGORY_UNKNOWN;
        }
        String className = e.getClass().getSimpleName().toLowerCase();
        if (className.contains("validation") || className.contains("constraint") || className.contains("shell")) {
            return ReleaseLifecycleMetrics.EX_CATEGORY_VALIDATION;
        }
        if (className.contains("access") || className.contains("auth") || className.contains("scope")
                || className.contains("forbidden") || className.contains("unauthorized")) {
            return ReleaseLifecycleMetrics.EX_CATEGORY_AUTHORIZATION;
        }
        if (className.contains("dispatch") || className.contains("messaging") || className.contains("kafka")) {
            return ReleaseLifecycleMetrics.EX_CATEGORY_DISPATCH;
        }
        return ReleaseLifecycleMetrics.EX_CATEGORY_UNKNOWN;
    }
}
