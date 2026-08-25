package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.command.ReleaseCommandDispatcher;
import com.opsera.integrator.sfdc.command.ReleaseDispatchRequest;
import com.opsera.integrator.sfdc.command.ReleaseDispatchResult;
import com.opsera.integrator.sfdc.command.WorkerDispatchProperties;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.V2UnsupportedOperationException;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Facade service for v2 release command submission.
 *
 * <p>Validates the canonical command, resolves a correlation identifier, and routes
 * execution to either the {@link ReleaseCommandDispatcher} (worker path when enabled) or the
 * existing legacy service calls (fallback). Returns a typed {@link AcceptedAcknowledgement}
 * without blocking on Salesforce CLI execution.
 *
 * <p>Correlation resolution priority:
 * <ol>
 *   <li>MDC correlation value placed by {@code CorrelationIdFilter} at HTTP ingress</li>
 *   <li>Caller-supplied {@code clientCorrelationId} field (optional)</li>
 *   <li>Freshly generated UUID</li>
 * </ol>
 *
 * <p>Worker dispatch is enabled on a per-command-type basis via {@link WorkerDispatchProperties}.
 * When worker dispatch is disabled for a command type, the legacy execution path is used and
 * the returned {@link AcceptedAcknowledgement} is identical to the pre-dispatcher behavior.
 *
 * <p>CANCEL is routed through the dispatcher cancel path when worker dispatch is enabled,
 * or throws {@link V2UnsupportedOperationException} when legacy cancel is not configured.
 */
@Service
public class ReleaseCommandFacade {

    static final String STATUS_URL_TEMPLATE = "/api/v2/sfdc/release-jobs/%s/status";

    private final QuickDeployService quickDeployService;
    private final SfdcIntegratorService sfdcIntegratorService;
    private final ReleaseCommandDispatcher releaseCommandDispatcher;
    private final WorkerDispatchProperties workerDispatchProperties;

    public ReleaseCommandFacade(QuickDeployService quickDeployService,
                                 SfdcIntegratorService sfdcIntegratorService,
                                 ReleaseCommandDispatcher releaseCommandDispatcher,
                                 WorkerDispatchProperties workerDispatchProperties) {
        this.quickDeployService = quickDeployService;
        this.sfdcIntegratorService = sfdcIntegratorService;
        this.releaseCommandDispatcher = releaseCommandDispatcher;
        this.workerDispatchProperties = workerDispatchProperties;
    }

    /**
     * Accepts a validated v2 release command, routes to worker or legacy execution, and returns
     * an {@link AcceptedAcknowledgement}.
     *
     * @param command a validated {@link ReleaseCommandRequest}
     * @return accepted acknowledgement with job identity and status URL
     * @throws V2UnsupportedOperationException if CANCEL is received without worker dispatch enabled
     */
    public AcceptedAcknowledgement accept(ReleaseCommandRequest command) {
        String correlationId = resolveCorrelationId(command);
        String operationTypeName = command.getOperationType().name();

        if (workerDispatchProperties.isWorkerDispatchEnabled(operationTypeName)) {
            return acceptViaDispatcher(command, correlationId, operationTypeName);
        }
        return acceptViaLegacy(command, correlationId);
    }

    private AcceptedAcknowledgement acceptViaDispatcher(ReleaseCommandRequest command,
                                                        String correlationId,
                                                        String operationTypeName) {
        ReleaseDispatchRequest dispatchRequest = ReleaseDispatchRequest.builder()
                .commandType(operationTypeName)
                .correlationId(correlationId)
                .idempotencyKey(command.getClientCorrelationId())
                .pipelineId(command.getPipelineId())
                .stepId(command.getStepId())
                .customerId(command.getCustomerId())
                .sfdcToolId(command.getSfdcToolId())
                .deploymentRequestId(command.getDeployRequestId())
                .fallbackTaskId(command.getGitTaskId() != null ? command.getGitTaskId() : "")
                .timeoutSeconds(workerDispatchProperties.getTimeoutSeconds())
                .build();

        ReleaseDispatchResult result = releaseCommandDispatcher.dispatch(dispatchRequest);

        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(result.getJobId());
        ack.setCorrelationId(result.getCorrelationId());
        ack.setStatusUrl(result.getStatusPath() != null
                ? result.getStatusPath()
                : String.format(STATUS_URL_TEMPLATE, result.getJobId()));
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(result.getAcceptedAt() != null ? result.getAcceptedAt() : Instant.now());
        ack.setOperationType(command.getOperationType());
        return ack;
    }

    private AcceptedAcknowledgement acceptViaLegacy(ReleaseCommandRequest command,
                                                    String correlationId) {
        String jobId = UUID.randomUUID().toString();

        switch (command.getOperationType()) {
            case QUICK_DEPLOY -> quickDeployService.start(buildQuickDeployRequest(command));
            case DEPLOY -> sfdcIntegratorService.deploy(buildDeployRequest(command));
            case VALIDATE -> sfdcIntegratorService.validate(buildDeployRequest(command));
            case CANCEL -> throw new V2UnsupportedOperationException("CANCEL");
        }

        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(jobId);
        ack.setCorrelationId(correlationId);
        ack.setStatusUrl(String.format(STATUS_URL_TEMPLATE, jobId));
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.now());
        ack.setOperationType(command.getOperationType());
        return ack;
    }

    private String resolveCorrelationId(ReleaseCommandRequest command) {
        String mdcId = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (mdcId != null && !mdcId.isBlank()) {
            return mdcId;
        }
        String clientId = command.getClientCorrelationId();
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        return UUID.randomUUID().toString();
    }

    private QuickDeployRequest buildQuickDeployRequest(ReleaseCommandRequest command) {
        QuickDeployRequest req = new QuickDeployRequest();
        String deployRequestId = command.getDeployRequestId();
        req.setDeploymentRequestId(
                deployRequestId != null && !deployRequestId.isBlank() ? deployRequestId : command.getTaskId());
        req.setPipelineId(command.getPipelineId());
        req.setStepId(command.getStepId());
        req.setFallbackTaskId(command.getGitTaskId() != null ? command.getGitTaskId() : "");
        return req;
    }

    private DeployRequest buildDeployRequest(ReleaseCommandRequest command) {
        DeployRequest req = new DeployRequest();
        req.setPipelineId(command.getPipelineId());
        req.setStepId(command.getStepId());
        return req;
    }
}
