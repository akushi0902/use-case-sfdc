package com.opsera.integrator.sfdc.command;

import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link ReleaseCommandDispatcher} implementation that routes release commands to either
 * the Kubernetes worker path or the legacy service path.
 *
 * <p>Routing decision:
 * <ul>
 *   <li>Worker dispatch is selected when {@link WorkerDispatchProperties#isWorkerDispatchEnabled(String)}
 *       returns true for the command type.</li>
 *   <li>Otherwise, the legacy execution path is used: {@link QuickDeployService} for QUICK_DEPLOY
 *       and CANCEL, {@link SfdcIntegratorService} for DEPLOY and VALIDATE.</li>
 * </ul>
 *
 * <p>Worker dispatch is initiated via {@link WorkerReleaseCommandAdapter}, which bridges to
 * Kubernetes Job submission without blocking on Salesforce execution.
 *
 * <p>Dispatch failures are categorized as:
 * <ul>
 *   <li>{@code ROUTING_DISABLED} — worker dispatch is configured off for this command type</li>
 *   <li>{@code WORKER_UNAVAILABLE} — worker adapter threw during handoff initiation</li>
 *   <li>{@code LEGACY_FALLBACK_FAILED} — legacy service call failed</li>
 * </ul>
 *
 * <p>Safe logging: only pipelineId, stepId, commandType, and correlationId appear in logs.
 * Raw request payloads, credentials, and org URLs are never logged.
 */
@Service
public class RoutingReleaseCommandDispatcher implements ReleaseCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RoutingReleaseCommandDispatcher.class);
    static final String STATUS_PATH_TEMPLATE = "/api/v2/sfdc/release-jobs/%s/status";

    static final String CMD_QUICK_DEPLOY = "QUICK_DEPLOY";
    static final String CMD_DEPLOY = "DEPLOY";
    static final String CMD_VALIDATE = "VALIDATE";
    static final String CMD_CANCEL = "CANCEL";

    private final WorkerDispatchProperties workerDispatchProperties;
    private final WorkerReleaseCommandAdapter workerAdapter;
    private final QuickDeployService quickDeployService;
    private final SfdcIntegratorService sfdcIntegratorService;

    public RoutingReleaseCommandDispatcher(WorkerDispatchProperties workerDispatchProperties,
                                           WorkerReleaseCommandAdapter workerAdapter,
                                           QuickDeployService quickDeployService,
                                           SfdcIntegratorService sfdcIntegratorService) {
        this.workerDispatchProperties = workerDispatchProperties;
        this.workerAdapter = workerAdapter;
        this.quickDeployService = quickDeployService;
        this.sfdcIntegratorService = sfdcIntegratorService;
    }

    @Override
    public ReleaseDispatchResult dispatch(ReleaseDispatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReleaseDispatchRequest must not be null");
        }

        String commandType = request.getCommandType();

        if (workerDispatchProperties.isWorkerDispatchEnabled(commandType)) {
            return dispatchToWorker(request);
        }
        return dispatchToLegacy(request);
    }

    @Override
    public ReleaseDispatchResult cancel(ReleaseDispatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReleaseDispatchRequest must not be null");
        }

        String commandType = request.getCommandType() != null ? request.getCommandType() : CMD_CANCEL;

        if (workerDispatchProperties.isWorkerDispatchEnabled(commandType)
                || workerDispatchProperties.isWorkerDispatchEnabled(CMD_CANCEL)) {
            return cancelViaWorker(request);
        }
        return cancelViaLegacy(request);
    }

    private ReleaseDispatchResult dispatchToWorker(ReleaseDispatchRequest request) {
        String correlationId = resolveCorrelationId(request.getCorrelationId());
        String jobId = generateJobId();
        Instant now = Instant.now();

        log.info("Worker dispatch: commandType='{}' correlationId='{}' pipelineId='{}' stepId='{}'",
                request.getCommandType(), correlationId,
                request.getPipelineId(), request.getStepId());

        int timeout = request.getTimeoutSeconds() > 0
                ? request.getTimeoutSeconds()
                : workerDispatchProperties.getTimeoutSeconds();

        AsyncCommandRequest asyncCommand = AsyncCommandRequest.builder()
                .operationType(request.getCommandType())
                .correlationId(correlationId)
                .idempotencyKey(request.getIdempotencyKey())
                .pipelineId(request.getPipelineId())
                .stepId(request.getStepId())
                .sfdcToolId(request.getSfdcToolId())
                .deploymentRequestId(request.getDeploymentRequestId())
                .dataClassification("CONFIDENTIAL")
                .build();

        try {
            workerAdapter.execute(asyncCommand);
        } catch (Exception e) {
            log.warn("WorkerReleaseCommandAdapter failed for commandType='{}' correlationId='{}': {}",
                    request.getCommandType(), correlationId, e.getMessage());
            throw new CommandDispatchException(jobId, correlationId,
                    "WORKER_UNAVAILABLE",
                    "Worker adapter handoff failed for commandType='" + request.getCommandType() + "'",
                    e);
        }

        log.info("Worker dispatch accepted: jobId='{}' correlationId='{}' commandType='{}' timeoutSeconds={}",
                jobId, correlationId, request.getCommandType(), timeout);

        return ReleaseDispatchResult.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .commandType(request.getCommandType())
                .routedTo(ReleaseDispatchResult.ROUTED_TO_WORKER)
                .statusPath(String.format(STATUS_PATH_TEMPLATE, jobId))
                .acceptedAt(now)
                .idempotentReplay(false)
                .build();
    }

    private ReleaseDispatchResult dispatchToLegacy(ReleaseDispatchRequest request) {
        log.debug("Legacy dispatch: commandType='{}' pipelineId='{}' stepId='{}'",
                request.getCommandType(), request.getPipelineId(), request.getStepId());

        String jobId = generateJobId();
        String correlationId = resolveCorrelationId(request.getCorrelationId());
        Instant now = Instant.now();

        switch (request.getCommandType()) {
            case CMD_QUICK_DEPLOY -> {
                QuickDeployRequest qdr = new QuickDeployRequest();
                qdr.setDeploymentRequestId(request.getDeploymentRequestId() != null
                        ? request.getDeploymentRequestId() : "");
                qdr.setPipelineId(request.getPipelineId());
                qdr.setStepId(request.getStepId());
                qdr.setFallbackTaskId(request.getFallbackTaskId() != null
                        ? request.getFallbackTaskId() : "");
                quickDeployService.start(qdr);
            }
            case CMD_DEPLOY -> {
                DeployRequest dr = new DeployRequest();
                dr.setPipelineId(request.getPipelineId());
                dr.setStepId(request.getStepId());
                sfdcIntegratorService.deploy(dr);
            }
            case CMD_VALIDATE -> {
                DeployRequest dr = new DeployRequest();
                dr.setPipelineId(request.getPipelineId());
                dr.setStepId(request.getStepId());
                sfdcIntegratorService.validate(dr);
            }
            default -> throw new CommandDispatchException(jobId, correlationId,
                    "ROUTING_DISABLED",
                    "No legacy path for commandType: " + request.getCommandType(),
                    null);
        }

        return ReleaseDispatchResult.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .commandType(request.getCommandType())
                .routedTo(ReleaseDispatchResult.ROUTED_TO_LEGACY)
                .statusPath(String.format(STATUS_PATH_TEMPLATE, jobId))
                .acceptedAt(now)
                .idempotentReplay(false)
                .build();
    }

    private ReleaseDispatchResult cancelViaWorker(ReleaseDispatchRequest request) {
        String correlationId = resolveCorrelationId(request.getCorrelationId());
        String jobId = generateJobId();

        log.info("Worker cancel: commandType='{}' correlationId='{}' pipelineId='{}'",
                request.getCommandType(), correlationId, request.getPipelineId());

        // Worker cancellation: signals the Kubernetes Job for graceful termination.
        // Full implementation wires into K8s API when the worker scheduling platform is available.

        return ReleaseDispatchResult.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .commandType(CMD_CANCEL)
                .routedTo(ReleaseDispatchResult.ROUTED_TO_WORKER)
                .statusPath(String.format(STATUS_PATH_TEMPLATE, jobId))
                .acceptedAt(Instant.now())
                .build();
    }

    private ReleaseDispatchResult cancelViaLegacy(ReleaseDispatchRequest request) {
        log.debug("Legacy cancel: correlationId='{}' pipelineId='{}'",
                request.getCorrelationId(), request.getPipelineId());

        String jobId = generateJobId();
        String correlationId = resolveCorrelationId(request.getCorrelationId());

        QuickDeployStopRequest stopRequest = new QuickDeployStopRequest();
        stopRequest.setDeploymentRequestId(request.getDeploymentRequestId() != null
                ? request.getDeploymentRequestId() : "");
        stopRequest.setPipelineId(request.getPipelineId());
        quickDeployService.stop(stopRequest);

        return ReleaseDispatchResult.builder()
                .jobId(jobId)
                .correlationId(correlationId)
                .commandType(CMD_CANCEL)
                .routedTo(ReleaseDispatchResult.ROUTED_TO_LEGACY)
                .statusPath(String.format(STATUS_PATH_TEMPLATE, jobId))
                .acceptedAt(Instant.now())
                .build();
    }

    private String resolveCorrelationId(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return "cid-" + UUID.randomUUID();
    }

    private String generateJobId() {
        return "job-" + UUID.randomUUID();
    }
}
