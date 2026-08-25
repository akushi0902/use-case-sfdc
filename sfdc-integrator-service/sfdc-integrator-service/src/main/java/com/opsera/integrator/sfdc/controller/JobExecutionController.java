package com.opsera.integrator.sfdc.controller;

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
import com.opsera.integrator.sfdc.security.CallerContextResolver;
import com.opsera.integrator.sfdc.security.ScopeAuthorizer;
import com.opsera.integrator.sfdc.security.ScopeConstants;
import com.opsera.integrator.sfdc.security.ShellArgumentProfile;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import jakarta.validation.Valid;
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

    public JobExecutionController(QuickDeployService quickDeployService,
                                   ClassificationPolicyResolver classificationResolver,
                                   SafeStructuredLogger safeLogger,
                                   AuditEventWriter auditWriter,
                                   ShellArgumentValidator shellArgumentValidator,
                                   CallerContextResolver callerContextResolver,
                                   ScopeAuthorizer scopeAuthorizer) {
        this.quickDeployService = quickDeployService;
        this.classificationResolver = classificationResolver;
        this.safeLogger = safeLogger;
        this.auditWriter = auditWriter;
        this.shellArgumentValidator = shellArgumentValidator;
        this.callerContextResolver = callerContextResolver;
        this.scopeAuthorizer = scopeAuthorizer;
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

        return ResponseEntity.ok(SUCCESS);
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

        return ResponseEntity.ok(SUCCESS);
    }
}
