package com.opsera.integrator.sfdc.resources.v2.cancel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional request body for v2 job cancellation.
 *
 * <p>All fields are optional — the endpoint accepts an empty body or no body at all.
 * {@code safeReason} is sanitised to the allow-listed maximum length; raw free-text
 * content must not reference credentials, tokens, or metadata XML.
 *
 * <p>toString() omits {@code requestedBy} to prevent caller-identity metadata from
 * appearing in application logs.
 */
@Schema(description = "Optional cancellation request body. All fields are optional.")
public class CancellationRequest {

    @Schema(description = "Safe operator-supplied reason for cancellation (max 256 characters). "
            + "Must not contain credentials, tokens, or raw XML.",
            example = "Cancelling stale deployment — superseded by newer version")
    @Size(max = 256, message = "safeReason must not exceed 256 characters")
    private String safeReason;

    @Schema(description = "Optional caller-supplied correlation ID (max 64 characters).",
            example = "corr-cancel-001", maxLength = 64)
    @Size(max = 64, message = "clientCorrelationId must not exceed 64 characters")
    private String clientCorrelationId;

    @Schema(description = "Optional caller identity. Omitted from logs to prevent user data exposure.",
            hidden = true)
    private String requestedBy;

    public CancellationRequest() {}

    public String getSafeReason() { return safeReason; }
    public void setSafeReason(String safeReason) { this.safeReason = safeReason; }

    public String getClientCorrelationId() { return clientCorrelationId; }
    public void setClientCorrelationId(String clientCorrelationId) {
        this.clientCorrelationId = clientCorrelationId;
    }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    @Override
    public String toString() {
        return "CancellationRequest{safeReason='" + safeReason + "'}";
    }
}
