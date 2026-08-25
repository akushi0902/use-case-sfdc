package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Execution adapter that bridges {@link AsyncCommandRequest} to the legacy
 * {@link SfdcIntegratorService#deploy(DeployRequest)} without modifying the service's internals.
 *
 * <p>This adapter preserves the existing service contract: it maps command fields to a
 * {@link DeployRequest} and delegates immediately.
 *
 * <p>Only safe, operational fields (pipelineId, stepId) are logged. Raw credentials, Org URLs,
 * and deployment request payloads are not logged.
 */
@Component
public class DeployCommandAdapter implements ReleaseCommandAdapter {

    private static final Logger log = LoggerFactory.getLogger(DeployCommandAdapter.class);
    static final String OPERATION_TYPE = "DEPLOY";

    private final SfdcIntegratorService sfdcIntegratorService;

    public DeployCommandAdapter(SfdcIntegratorService sfdcIntegratorService) {
        this.sfdcIntegratorService = sfdcIntegratorService;
    }

    @Override
    public String supportedOperationType() {
        return OPERATION_TYPE;
    }

    @Override
    public void execute(AsyncCommandRequest command) {
        log.debug("DeployCommandAdapter.execute: pipelineId='{}' stepId='{}'",
                command.getPipelineId(), command.getStepId());

        DeployRequest request = new DeployRequest();
        request.setPipelineId(command.getPipelineId());
        request.setStepId(command.getStepId());

        sfdcIntegratorService.deploy(request);
    }
}
