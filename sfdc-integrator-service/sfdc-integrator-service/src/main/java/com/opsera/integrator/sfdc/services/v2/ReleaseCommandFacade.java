package com.opsera.integrator.sfdc.services.v2;

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
 * <p>Validates the canonical command, resolves a correlation identifier, delegates
 * to the appropriate existing execution service, and returns a typed
 * {@link AcceptedAcknowledgement} without blocking on Salesforce CLI execution.
 *
 * <p>Correlation resolution priority:
 * <ol>
 *   <li>MDC correlation value placed by {@code CorrelationIdFilter} at HTTP ingress</li>
 *   <li>Caller-supplied {@code clientCorrelationId} field (optional)</li>
 *   <li>Freshly generated UUID</li>
 * </ol>
 *
 * <p>CANCEL is not yet delegated to a legacy path; it throws
 * {@link V2UnsupportedOperationException} so the shared exception handler returns
 * a structured 422 response.
 */
@Service
public class ReleaseCommandFacade {

    static final String STATUS_URL_TEMPLATE = "/api/v2/sfdc/release-jobs/%s/status";

    private final QuickDeployService quickDeployService;
    private final SfdcIntegratorService sfdcIntegratorService;

    public ReleaseCommandFacade(QuickDeployService quickDeployService,
                                 SfdcIntegratorService sfdcIntegratorService) {
        this.quickDeployService = quickDeployService;
        this.sfdcIntegratorService = sfdcIntegratorService;
    }

    /**
     * Accepts a validated v2 release command, delegates to the appropriate legacy
     * execution service, and returns an {@link AcceptedAcknowledgement}.
     *
     * @param command a validated {@link ReleaseCommandRequest}
     * @return accepted acknowledgement with job identity and status URL
     * @throws V2UnsupportedOperationException if the operation type has no delegated implementation
     */
    public AcceptedAcknowledgement accept(ReleaseCommandRequest command) {
        String correlationId = resolveCorrelationId(command);
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
