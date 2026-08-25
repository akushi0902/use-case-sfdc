package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.ErrorResponse;
import com.opsera.integrator.sfdc.exceptions.JobIdFormatException;
import com.opsera.integrator.sfdc.exceptions.JobStatusNotFoundException;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.services.v2.JobStatusAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * V2 controller for release job status queries.
 *
 * <p>Provides GET /api/v2/sfdc/release-jobs/{jobId}/status — the status URL returned
 * by the v2 submission facade in {@code AcceptedAcknowledgement.statusUrl}.
 *
 * <p>Path variable {@code jobId} is validated before any status source is queried:
 * <ul>
 *   <li>Must not be blank or null</li>
 *   <li>Maximum {@value #MAX_JOB_ID_LENGTH} characters</li>
 *   <li>Must match the safe pattern: alphanumeric, hyphen, underscore only</li>
 * </ul>
 *
 * <p>Delegates to {@link JobStatusAdapter} — the current implementation reads from
 * the durable lifecycle job repository. The adapter boundary allows future replacement
 * without changing this controller.
 */
@Tag(name = "V2 Release Jobs", description = "Typed release job commands. Use these endpoints for new integrations.")
@RestController
@RequestMapping("/api/v2/sfdc/release-jobs")
public class ReleaseJobStatusController {

    static final int MAX_JOB_ID_LENGTH = 128;
    static final Pattern SAFE_JOB_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]+$");

    private final JobStatusAdapter statusAdapter;
    private final SafeStructuredLogger safeLogger;
    private final ReleaseCoexistenceTelemetry telemetry;

    public ReleaseJobStatusController(JobStatusAdapter statusAdapter,
                                      SafeStructuredLogger safeLogger,
                                      ReleaseCoexistenceTelemetry telemetry) {
        this.statusAdapter = statusAdapter;
        this.safeLogger = safeLogger;
        this.telemetry = telemetry;
    }

    /**
     * Returns the current lifecycle status of a release job.
     *
     * <p>Returns HTTP 200 with the typed status response for known jobs.
     * Returns HTTP 404 for well-formed but unknown job identifiers.
     * Returns HTTP 400 for malformed job identifiers (before querying the status source).
     */
    @Operation(
            summary = "Get v2 release job status",
            description = "Returns the current lifecycle state, checkpoint history, and safe diagnostic summary "
                    + "for a release job. The jobId is returned in the 202 Accepted response from job submission. "
                    + "Does not require live Salesforce, Kafka, Hazelcast, or database connectivity "
                    + "beyond the durable lifecycle store.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found — lifecycle status returned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReleaseJobStatusResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed job identifier — validation failed before querying status source",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401",
                    description = "Authentication required — valid Bearer JWT must be provided",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Job not found — well-formed identifier with no matching record",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503",
                    description = "Status source temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/{jobId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReleaseJobStatusResponse> getJobStatus(
            @Parameter(description = "Unique job identifier returned by the submission endpoint",
                    example = "job-example-00000000-0000-0000-0000-000000000001",
                    required = true)
            @PathVariable String jobId) {

        long startMs = System.currentTimeMillis();
        String correlationId = getCorrelationId();
        validateJobId(jobId, correlationId);

        return statusAdapter.findByJobId(jobId)
                .map(resp -> {
                    safeLogger.logEvent(SafeLogEvent.builder()
                            .operation("v2-release-status-query")
                            .controller("ReleaseJobStatusController")
                            .outcome(SafeLogEvent.Outcome.COMPLETED)
                            .safeField("routeVersion", ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2)
                            .safeField("operationType", resp.getOperationType() != null
                                    ? ReleaseCoexistenceTelemetry.resolveOperationType(resp.getOperationType())
                                    : ReleaseCoexistenceTelemetry.OPERATION_UNKNOWN)
                            .safeField("outcome", ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED)
                            .build());
                    telemetry.record(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2,
                            resp.getOperationType() != null
                                    ? ReleaseCoexistenceTelemetry.resolveOperationType(resp.getOperationType())
                                    : ReleaseCoexistenceTelemetry.OPERATION_UNKNOWN,
                            ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED,
                            ReleaseCoexistenceTelemetry.ENDPOINT_STATUS,
                            System.currentTimeMillis() - startMs);
                    return ResponseEntity.ok(resp);
                })
                .orElseThrow(() -> {
                    safeLogger.logEvent(SafeLogEvent.builder()
                            .operation("v2-release-status-query")
                            .controller("ReleaseJobStatusController")
                            .outcome(SafeLogEvent.Outcome.REJECTED)
                            .safeField("routeVersion", ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2)
                            .safeField("outcome", ReleaseCoexistenceTelemetry.OUTCOME_NOT_FOUND)
                            .build());
                    telemetry.record(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2,
                            ReleaseCoexistenceTelemetry.OPERATION_UNKNOWN,
                            ReleaseCoexistenceTelemetry.OUTCOME_NOT_FOUND,
                            ReleaseCoexistenceTelemetry.ENDPOINT_STATUS,
                            System.currentTimeMillis() - startMs);
                    return new JobStatusNotFoundException(jobId, correlationId);
                });
    }

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

    private String getCorrelationId() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return id != null ? id : "unknown";
    }
}
