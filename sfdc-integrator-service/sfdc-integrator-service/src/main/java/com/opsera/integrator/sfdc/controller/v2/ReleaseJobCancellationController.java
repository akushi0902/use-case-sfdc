package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.ErrorResponse;
import com.opsera.integrator.sfdc.exceptions.JobIdFormatException;
import com.opsera.integrator.sfdc.exceptions.JobNotCancellableException;
import com.opsera.integrator.sfdc.exceptions.JobStatusNotFoundException;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.TransitionResult;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.resources.v2.cancel.CancellationAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.cancel.CancellationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * V2 controller for release job cancellation.
 *
 * <p>Provides {@code POST /api/v2/sfdc/jobs/{jobId}/cancel} — a consistent v2 cancellation
 * route for deployment, validation, and quick deploy jobs accepted through the v2 submission API.
 *
 * <p>Cancellation is state-gated: only active jobs (ACCEPTED, DISPATCHING, RUNNING) can be
 * cancelled. Terminal jobs return HTTP 409. Unknown job identifiers return HTTP 404.
 *
 * <p>The legacy {@code POST /quickdeploy/stop} route is unchanged and handled by
 * {@code JobExecutionController}.
 *
 * <p>Path variable validation mirrors {@link ReleaseJobStatusController}: alphanumeric,
 * hyphen, underscore only, maximum {@value ReleaseJobStatusController#MAX_JOB_ID_LENGTH} characters.
 */
@Tag(name = "V2 Release Jobs", description = "Typed release job commands. Use these endpoints for new integrations.")
@RestController
@RequestMapping("/api/v2/sfdc/jobs")
public class ReleaseJobCancellationController {

    static final Pattern SAFE_JOB_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]+$");
    static final int MAX_JOB_ID_LENGTH = ReleaseJobStatusController.MAX_JOB_ID_LENGTH;
    static final String STATUS_URL_TEMPLATE = "/api/v2/sfdc/release-jobs/%s/status";

    private final JobRepository jobRepository;
    private final JobLifecycleService lifecycleService;
    private final SafeStructuredLogger safeLogger;

    public ReleaseJobCancellationController(JobRepository jobRepository,
                                             JobLifecycleService lifecycleService,
                                             SafeStructuredLogger safeLogger) {
        this.jobRepository = jobRepository;
        this.lifecycleService = lifecycleService;
        this.safeLogger = safeLogger;
    }

    /**
     * Cancels an active v2 release job.
     *
     * <p>State machine:
     * <ul>
     *   <li>Active job (ACCEPTED/DISPATCHING/RUNNING) → CANCELLED → HTTP 202</li>
     *   <li>Already CANCELLED (idempotent replay) → HTTP 202 with CANCELLED state</li>
     *   <li>Other terminal state → HTTP 409 via {@link JobNotCancellableException}</li>
     *   <li>Unknown job → HTTP 404 via {@link JobStatusNotFoundException}</li>
     *   <li>Malformed jobId → HTTP 400 via {@link JobIdFormatException}</li>
     * </ul>
     *
     * <p>The legacy {@code POST /quickdeploy/stop} is not affected by this endpoint.
     */
    @Operation(
            summary = "Cancel a v2 release job",
            description = "Cancels an active deployment, validation, or quick deploy job "
                    + "accepted through the v2 submission API. "
                    + "Returns 202 for accepted or already-cancelled jobs, "
                    + "409 for other terminal states, and 404 for unknown jobs.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Cancellation accepted or already cancelled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CancellationAcknowledgement.class))),
            @ApiResponse(responseCode = "400", description = "Malformed job identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Job is in a terminal state and cannot be cancelled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{jobId}/cancel",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "*/*"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationAcknowledgement> cancelJob(
            @Parameter(description = "Unique job identifier returned by the submission endpoint",
                    required = true)
            @PathVariable String jobId,
            @Valid @RequestBody(required = false) CancellationRequest request) {

        String correlationId = resolveCorrelationId();
        validateJobId(jobId, correlationId);

        JobRecord job = jobRepository.findById(jobId)
                .orElseThrow(() -> {
                    safeLogger.logEvent(SafeLogEvent.builder()
                            .operation("v2-cancel-job")
                            .controller("ReleaseJobCancellationController")
                            .outcome(SafeLogEvent.Outcome.REJECTED)
                            .safeField("reason", "job-not-found")
                            .build());
                    return new JobStatusNotFoundException(jobId, correlationId);
                });

        JobLifecycleState currentState = JobLifecycleState.fromString(job.getCurrentState());

        // Already CANCELLED: idempotent — return 202 without re-transitioning
        if (currentState == JobLifecycleState.CANCELLED) {
            safeLogger.logEvent(SafeLogEvent.builder()
                    .operation("v2-cancel-job")
                    .controller("ReleaseJobCancellationController")
                    .outcome(SafeLogEvent.Outcome.COMPLETED)
                    .safeField("result", "idempotent-already-cancelled")
                    .build());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(buildAck(job, correlationId, "CANCELLED", "Job was already cancelled"));
        }

        // Other terminal state: not cancellable
        if (currentState.isTerminal()) {
            safeLogger.logEvent(SafeLogEvent.builder()
                    .operation("v2-cancel-job")
                    .controller("ReleaseJobCancellationController")
                    .outcome(SafeLogEvent.Outcome.REJECTED)
                    .safeField("reason", "terminal-state")
                    .build());
            throw new JobNotCancellableException(jobId, currentState.name(), correlationId);
        }

        // Active: attempt cancellation
        String safeReasonCode = resolveReasonCode(request);
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("v2-cancel-job")
                .controller("ReleaseJobCancellationController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("safeReasonCode", safeReasonCode)
                .build());

        TransitionResult result = lifecycleService.markCancelled(jobId, correlationId, safeReasonCode);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(buildAck(job, result.getCorrelationId(), result.getToState().name(),
                        "Cancellation accepted"));
    }

    // ---- private helpers ----

    private void validateJobId(String jobId, String correlationId) {
        if (jobId == null || jobId.isBlank()) {
            throw new JobIdFormatException(correlationId, "jobId must not be blank");
        }
        if (jobId.length() > MAX_JOB_ID_LENGTH) {
            throw new JobIdFormatException(correlationId,
                    "jobId exceeds maximum length of " + MAX_JOB_ID_LENGTH + " characters");
        }
        if (!SAFE_JOB_ID_PATTERN.matcher(jobId).matches()) {
            throw new JobIdFormatException(correlationId,
                    "jobId contains characters that are not permitted (alphanumeric, hyphen, underscore only)");
        }
    }

    private String resolveCorrelationId() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return id != null && !id.isBlank() ? id : "unknown";
    }

    private String resolveReasonCode(CancellationRequest request) {
        if (request != null && request.getSafeReason() != null && !request.getSafeReason().isBlank()) {
            return "OPERATOR_REQUESTED_CANCELLATION";
        }
        return "OPERATOR_REQUESTED_CANCELLATION";
    }

    private CancellationAcknowledgement buildAck(JobRecord job, String correlationId,
                                                   String state, String safeMessage) {
        CancellationAcknowledgement ack = new CancellationAcknowledgement();
        ack.setJobId(job.getJobId());
        ack.setCorrelationId(correlationId);
        ack.setStatusUrl(String.format(STATUS_URL_TEMPLATE, job.getJobId()));
        ack.setState(state);
        ack.setCancellationRequestedAt(Instant.now());
        ack.setSafeMessage(safeMessage);
        return ack;
    }
}
