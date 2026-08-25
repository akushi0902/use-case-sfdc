package com.opsera.integrator.sfdc.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Execution adapter that targets Kubernetes worker pods for release operations.
 *
 * <p>This adapter is the worker-path counterpart to {@link QuickDeployCommandAdapter}.
 * When invoked by {@link DefaultCommandDispatcher}, it initiates a Kubernetes Job submission
 * for the accepted command. In this initial implementation the K8s submission is logged as an
 * intent; production K8s API calls will be wired in when the worker scheduling platform is
 * available.
 *
 * <p>Handles all release operation types ({@code QUICK_DEPLOY}, {@code DEPLOY}, {@code VALIDATE},
 * {@code CANCEL}). The {@link DefaultCommandDispatcher} selects this adapter when worker dispatch
 * is enabled for the command type via {@link WorkerDispatchProperties}.
 *
 * <p>Safe logging only — no raw credentials, org URLs, or request payloads are logged.
 */
@Component
public class WorkerReleaseCommandAdapter implements ReleaseCommandAdapter {

    private static final Logger log = LoggerFactory.getLogger(WorkerReleaseCommandAdapter.class);

    static final String OPERATION_TYPE_WORKER = "WORKER_DISPATCH";

    @Override
    public String supportedOperationType() {
        return OPERATION_TYPE_WORKER;
    }

    @Override
    public void execute(AsyncCommandRequest command) {
        if (command == null) {
            throw new IllegalArgumentException("AsyncCommandRequest must not be null");
        }

        log.info("WorkerReleaseCommandAdapter: dispatching to worker. operationType='{}' pipelineId='{}' stepId='{}'",
                command.getOperationType(), command.getPipelineId(), command.getStepId());

        // Worker pod submission: the Kubernetes Job is created here using the worker-job-template
        // defined in k8s/worker-job-template.yaml. The job carries:
        //   - correlationId for tracing
        //   - operationType to select the correct release command handler
        //   - sfdcToolId and pipelineId/stepId for safe operational routing
        //
        // Full K8s API submission is wired when the worker scheduling platform is available.
        // This adapter completes without blocking on Salesforce execution.

        log.debug("WorkerReleaseCommandAdapter: worker dispatch initiated. correlationId='{}' operationType='{}'",
                command.getCorrelationId(), command.getOperationType());
    }
}
