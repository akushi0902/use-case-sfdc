package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.ErrorResponse;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionRequest;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * V2 controller for release job submission.
 *
 * <p>Accepts typed release commands, delegates to {@link ReleaseCommandFacade}, and
 * returns HTTP 202 Accepted with a {@code Location} header pointing to the job status URL.
 * Legacy routes at {@code /quickdeploy} and {@code /deploy} remain unchanged.
 *
 * <p>Route availability is governed by {@link V2ReleaseRoutesProperties}:
 * <ul>
 *   <li>When {@code sfdc.v2.release.routes.enabled=false}, all v2 submission requests return
 *       HTTP 422 with a structured {@link ErrorResponse} carrying a correlation ID and
 *       remediation hint. Legacy routes are not affected.</li>
 *   <li>Per-operation flags in {@code sfdc.v2.release.routes.operations.*} deny specific
 *       operation types. Unknown or unconfigured types fail closed (denied by default).</li>
 * </ul>
 */
@Tag(name = "V2 Release Jobs", description = "Typed release job commands. Use these endpoints for new integrations. "
        + "Legacy endpoints (/quickdeploy, /deploy) remain available during coexistence.")
@RestController
@RequestMapping("/api/v2/sfdc/release-jobs")
public class ReleaseJobController {

    static final String ERROR_CODE_ROUTES_DISABLED = "V2_ROUTES_DISABLED";
    static final String ERROR_CODE_OPERATION_DISABLED = "V2_OPERATION_DISABLED";
    static final String REMEDIATION_LEGACY_FALLBACK =
            "V2 submission is temporarily unavailable. Use the legacy endpoints "
                    + "(/quickdeploy or /deploy) while v2 routes are disabled. "
                    + "See the operator runbook for rollback and re-enablement steps.";

    private final ReleaseCommandFacade facade;
    private final V2ReleaseRoutesProperties routesProperties;
    private final SafeStructuredLogger safeLogger;
    private final ReleaseCoexistenceTelemetry telemetry;
    private final QuickDeploySubmissionAdapter quickDeployAdapter;

    public ReleaseJobController(ReleaseCommandFacade facade,
            V2ReleaseRoutesProperties routesProperties,
            SafeStructuredLogger safeLogger,
            ReleaseCoexistenceTelemetry telemetry,
            QuickDeploySubmissionAdapter quickDeployAdapter) {
        this.facade = facade;
        this.routesProperties = routesProperties;
        this.safeLogger = safeLogger;
        this.telemetry = telemetry;
        this.quickDeployAdapter = quickDeployAdapter;
    }

    /**
     * Submits a v2 release command for asynchronous execution.
     *
     * <p>Returns HTTP 202 with a {@code Location} header and an
     * {@link AcceptedAcknowledgement} body on success.
     * Returns HTTP 422 when the v2 route family or the specific operation is disabled.
     * Returns HTTP 400 on validation failure (handled by {@code SfdcExceptionHandler}).
     * Returns HTTP 422 when the operation type is not yet supported.
     */
    @Operation(
            summary = "Submit a v2 release command",
            description = "Accepts a typed release command (DEPLOY, VALIDATE, QUICK_DEPLOY) for asynchronous "
                    + "execution. Returns 202 Accepted immediately with a jobId and statusUrl for polling. "
                    + "CANCEL returns 422 until full implementation is available. "
                    + "Returns 422 when v2 routes are disabled via operator configuration. "
                    + "Requires a valid Bearer JWT in the Authorization header.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Command accepted for asynchronous execution",
                    headers = @Header(name = "Location",
                            description = "URL for polling the job status",
                            schema = @Schema(type = "string", example = "/api/v2/sfdc/release-jobs/job-id/status")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AcceptedAcknowledgement.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure — one or more required fields are invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required — valid Bearer JWT must be provided",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient scope — JWT does not grant the required release permission",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "V2 routes disabled or operation not permitted by current configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitReleaseCommand(@Valid @RequestBody ReleaseCommandRequest request) {
        long startMs = System.currentTimeMillis();
        String operationType = request.getOperationType() != null
                ? ReleaseCoexistenceTelemetry.resolveOperationType(request.getOperationType().name())
                : ReleaseCoexistenceTelemetry.OPERATION_UNKNOWN;
        String correlationId = resolveCorrelationId();

        if (!routesProperties.isEnabled()) {
            return buildDisabledResponse(correlationId, operationType, startMs,
                    ERROR_CODE_ROUTES_DISABLED,
                    "V2 release routes are globally disabled by operator configuration.");
        }

        if (!routesProperties.isOperationEnabled(request.getOperationType() != null
                ? request.getOperationType().name() : "")) {
            return buildDisabledResponse(correlationId, operationType, startMs,
                    ERROR_CODE_OPERATION_DISABLED,
                    "Operation type '" + operationType + "' is not enabled in the v2 release routes configuration.");
        }

        AcceptedAcknowledgement ack = facade.accept(request);
        URI location = URI.create(ack.getStatusUrl());

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("v2-release-submit")
                .controller("ReleaseJobController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("routeVersion", ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2)
                .safeField("operationType", operationType)
                .safeField("outcome", ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED)
                .build());
        telemetry.record(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2, operationType,
                ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED,
                ReleaseCoexistenceTelemetry.ENDPOINT_SUBMISSION,
                System.currentTimeMillis() - startMs);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ack);
    }

    /**
     * Submits a quick deploy command via the dedicated v2 quick-deploy path.
     *
     * <p>The dedicated endpoint accepts a focused DTO with {@code deployRequestId}
     * as a required field, stricter than the general release command path.
     * Delegates to the same {@link ReleaseCommandFacade} after adapting the request.
     *
     * <p>Returns HTTP 202 with a {@code Location} header and an
     * {@link AcceptedAcknowledgement} body on success.
     * Returns HTTP 422 when the v2 route family or QUICK_DEPLOY operation is disabled.
     * Returns HTTP 400 on validation failure.
     */
    @Operation(
            summary = "Submit a v2 quick deploy command",
            description = "Accepts a quick deploy submission with required deployRequestId, "
                    + "customer context, tool context, and canonical task identifier. "
                    + "Returns 202 Accepted with jobId and statusUrl.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Quick deploy accepted for asynchronous execution",
                    headers = @Header(name = "Location",
                            description = "URL for polling the job status",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AcceptedAcknowledgement.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure — required fields missing or invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "V2 routes or QUICK_DEPLOY operation disabled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(path = "/quick-deploy",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitQuickDeploy(
            @Valid @RequestBody QuickDeploySubmissionRequest request) {
        long startMs = System.currentTimeMillis();
        String operationType = ReleaseCoexistenceTelemetry.resolveOperationType("QUICK_DEPLOY");
        String correlationId = resolveCorrelationId();

        if (!routesProperties.isEnabled()) {
            return buildDisabledResponse(correlationId, operationType, startMs,
                    ERROR_CODE_ROUTES_DISABLED,
                    "V2 release routes are globally disabled by operator configuration.");
        }

        if (!routesProperties.isOperationEnabled("QUICK_DEPLOY")) {
            return buildDisabledResponse(correlationId, operationType, startMs,
                    ERROR_CODE_OPERATION_DISABLED,
                    "Operation type 'QUICK_DEPLOY' is not enabled in the v2 release routes configuration.");
        }

        ReleaseCommandRequest cmd = quickDeployAdapter.toReleaseCommandRequest(request);
        AcceptedAcknowledgement ack = facade.accept(cmd);
        URI location = URI.create(ack.getStatusUrl());

        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("v2-quick-deploy-submit")
                .controller("ReleaseJobController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("routeVersion", ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2)
                .safeField("operationType", operationType)
                .safeField("outcome", ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED)
                .build());
        telemetry.record(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2, operationType,
                ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED,
                ReleaseCoexistenceTelemetry.ENDPOINT_SUBMISSION,
                System.currentTimeMillis() - startMs);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ack);
    }

    private ResponseEntity<ErrorResponse> buildDisabledResponse(String correlationId,
                                                                  String operationType,
                                                                  long startMs,
                                                                  String errorCode,
                                                                  String message) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("v2-release-submit")
                .controller("ReleaseJobController")
                .outcome(SafeLogEvent.Outcome.REJECTED)
                .safeField("routeVersion", ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2)
                .safeField("operationType", operationType)
                .safeField("outcome", ReleaseCoexistenceTelemetry.OUTCOME_DISABLED_ROUTE)
                .build());
        telemetry.record(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2, operationType,
                ReleaseCoexistenceTelemetry.OUTCOME_DISABLED_ROUTE,
                ReleaseCoexistenceTelemetry.ENDPOINT_SUBMISSION,
                System.currentTimeMillis() - startMs);

        ErrorResponse error = new ErrorResponse(correlationId, errorCode, message,
                REMEDIATION_LEGACY_FALLBACK);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    private String resolveCorrelationId() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return id != null && !id.isBlank() ? id : "unknown";
    }
}
