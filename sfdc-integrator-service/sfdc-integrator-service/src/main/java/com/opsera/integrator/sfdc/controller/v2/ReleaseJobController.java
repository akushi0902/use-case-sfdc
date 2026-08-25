package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.exceptions.ErrorResponse;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
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
import org.springframework.beans.factory.annotation.Value;
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
 * <p>The route family can be disabled at runtime via the
 * {@code sfdc.v2.release.routes.enabled} property (default: {@code true}).
 * When disabled, any request to this path returns HTTP 404 without affecting legacy routes.
 */
@Tag(name = "V2 Release Jobs", description = "Typed release job commands. Use these endpoints for new integrations. "
        + "Legacy endpoints (/quickdeploy, /deploy) remain available during coexistence.")
@RestController
@RequestMapping("/api/v2/sfdc/release-jobs")
public class ReleaseJobController {

    private final ReleaseCommandFacade facade;
    private final boolean routesEnabled;
    private final SafeStructuredLogger safeLogger;
    private final ReleaseCoexistenceTelemetry telemetry;

    public ReleaseJobController(ReleaseCommandFacade facade,
            @Value("${sfdc.v2.release.routes.enabled:true}") boolean routesEnabled,
            SafeStructuredLogger safeLogger,
            ReleaseCoexistenceTelemetry telemetry) {
        this.facade = facade;
        this.routesEnabled = routesEnabled;
        this.safeLogger = safeLogger;
        this.telemetry = telemetry;
    }

    /**
     * Submits a v2 release command for asynchronous execution.
     *
     * <p>Returns HTTP 202 with a {@code Location} header and an
     * {@link AcceptedAcknowledgement} body on success.
     * Returns HTTP 404 when the v2 route family is disabled.
     * Returns HTTP 400 on validation failure (handled by {@code SfdcExceptionHandler}).
     * Returns HTTP 422 when the operation type is not yet supported.
     */
    @Operation(
            summary = "Submit a v2 release command",
            description = "Accepts a typed release command (DEPLOY, VALIDATE, QUICK_DEPLOY) for asynchronous "
                    + "execution. Returns 202 Accepted immediately with a jobId and statusUrl for polling. "
                    + "CANCEL returns 422 until full implementation is available. "
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
            @ApiResponse(responseCode = "404", description = "V2 route family is disabled in this deployment",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Operation type not yet supported (e.g. CANCEL)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitReleaseCommand(@Valid @RequestBody ReleaseCommandRequest request) {
        long startMs = System.currentTimeMillis();
        String operationType = request.getOperationType() != null
                ? ReleaseCoexistenceTelemetry.resolveOperationType(request.getOperationType().name())
                : ReleaseCoexistenceTelemetry.OPERATION_UNKNOWN;

        if (!routesEnabled) {
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
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
}
