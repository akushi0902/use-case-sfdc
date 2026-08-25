package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Execution adapter that bridges {@link AsyncCommandRequest} to the legacy
 * {@link QuickDeployService#start(QuickDeployRequest)} without modifying the service's internals.
 *
 * <p>This adapter preserves the existing service contract: it maps command fields to a
 * {@link QuickDeployRequest} and delegates immediately. The service is expected to be
 * non-blocking (it enqueues work asynchronously rather than waiting for Salesforce completion).
 *
 * <p>Only safe, operational fields (pipelineId, stepId) are logged. Raw credentials, Org URLs,
 * and deployment request payloads are not logged.
 */
@Component
public class QuickDeployCommandAdapter implements ReleaseCommandAdapter {

    private static final Logger log = LoggerFactory.getLogger(QuickDeployCommandAdapter.class);
    static final String OPERATION_TYPE = "QUICK_DEPLOY";

    private final QuickDeployService quickDeployService;

    public QuickDeployCommandAdapter(QuickDeployService quickDeployService) {
        this.quickDeployService = quickDeployService;
    }

    @Override
    public String supportedOperationType() {
        return OPERATION_TYPE;
    }

    @Override
    public void execute(AsyncCommandRequest command) {
        log.debug("QuickDeployCommandAdapter.execute: pipelineId='{}' stepId='{}'",
                command.getPipelineId(), command.getStepId());

        QuickDeployRequest request = new QuickDeployRequest();
        request.setDeploymentRequestId(command.getDeploymentRequestId() != null
                ? command.getDeploymentRequestId() : "");
        request.setPipelineId(command.getPipelineId());
        request.setStepId(command.getStepId());
        // fallbackTaskId is not carried in AsyncCommandRequest; normalize to empty string
        request.setFallbackTaskId("");

        quickDeployService.start(request);
    }
}
