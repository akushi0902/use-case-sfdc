package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
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
@RestController
@RequestMapping("/api/v2/sfdc/release-jobs")
public class ReleaseJobController {

    private final ReleaseCommandFacade facade;
    private final boolean routesEnabled;

    public ReleaseJobController(ReleaseCommandFacade facade,
            @Value("${sfdc.v2.release.routes.enabled:true}") boolean routesEnabled) {
        this.facade = facade;
        this.routesEnabled = routesEnabled;
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
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitReleaseCommand(@Valid @RequestBody ReleaseCommandRequest request) {
        if (!routesEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        AcceptedAcknowledgement ack = facade.accept(request);
        URI location = URI.create(ack.getStatusUrl());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .body(ack);
    }
}
