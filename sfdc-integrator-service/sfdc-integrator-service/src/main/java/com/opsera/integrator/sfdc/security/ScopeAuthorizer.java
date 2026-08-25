package com.opsera.integrator.sfdc.security;

import com.opsera.integrator.sfdc.exceptions.ScopeAuthorizationException;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import org.springframework.stereotype.Component;

/**
 * Enforces service-level scope authorization against a resolved {@link CallerContext}.
 *
 * <p>Scope matching is exact and deny-by-default:
 * <ul>
 *   <li>The required scope must be present in the caller's scope set — broader or
 *       unrelated scopes do not satisfy a narrower required capability.</li>
 *   <li>Scope comparison is case-insensitive because {@link CallerContext} normalizes
 *       all scopes to lowercase at extraction time and {@link ScopeConstants} uses
 *       lowercase values.</li>
 *   <li>Whitespace-padded scope values passed as the required capability are trimmed
 *       before comparison to prevent accidental bypass via leading/trailing whitespace.</li>
 * </ul>
 *
 * <p>Denied decisions emit a safe {@link SafeLogEvent} containing only the correlation
 * identifier, safe actor reference, operation name, required capability, and denied
 * outcome. Raw JWT claims, token values, and request body content are never logged.
 *
 * <p>Throws {@link ScopeAuthorizationException} on denial — mapped to HTTP 403 by
 * {@link com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler}.
 */
@Component
public class ScopeAuthorizer {

    private final SafeStructuredLogger safeLogger;

    public ScopeAuthorizer(SafeStructuredLogger safeLogger) {
        this.safeLogger = safeLogger;
    }

    /**
     * Requires the caller to hold {@code requiredScope} before an operation proceeds.
     *
     * @param callerContext resolved caller context (may be null for unauthenticated paths)
     * @param requiredScope exact scope name the caller must hold (see {@link ScopeConstants})
     * @param operation     operation name used in denied-decision log events
     * @throws ScopeAuthorizationException when the caller is absent or lacks the required scope
     */
    public void requireScope(CallerContext callerContext, String requiredScope, String operation) {
        String normalizedScope = requiredScope != null ? requiredScope.trim() : "";

        if (callerContext == null || "none".equals(callerContext.getAuthenticationType())) {
            logDenial(operation, normalizedScope, "UNAUTHENTICATED",
                    "unknown",
                    callerContext != null ? callerContext.getCorrelationId() : "unknown");
            throw new ScopeAuthorizationException(
                    normalizedScope, "UNAUTHENTICATED",
                    callerContext != null ? callerContext.getCorrelationId() : "unknown",
                    "unknown");
        }

        if (!callerContext.hasScope(normalizedScope)) {
            String correlationId = callerContext.getCorrelationId();
            String safeActorRef = safeActorRef(callerContext);
            logDenial(operation, normalizedScope, "INSUFFICIENT_SCOPE", safeActorRef, correlationId);
            throw new ScopeAuthorizationException(
                    normalizedScope, "INSUFFICIENT_SCOPE", correlationId, safeActorRef);
        }
    }

    /**
     * Produces a safe actor reference from the caller context.
     * Never returns the raw subject, client ID, or any JWT claim value.
     */
    private String safeActorRef(CallerContext callerContext) {
        if (callerContext.hasSubject()) {
            // Return only a prefix hash-like indicator — never the full subject value
            return "subj:present";
        }
        if (callerContext.getClientId() != null && !callerContext.getClientId().isBlank()) {
            return "client:present";
        }
        return "unknown";
    }

    private void logDenial(String operation, String requiredCapability, String denialReason,
                            String safeActorRef, String correlationId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation(operation)
                .controller("ScopeAuthorizer")
                .correlationId(correlationId)
                .outcome(SafeLogEvent.Outcome.REJECTED)
                .safeField("requiredCapability", requiredCapability)
                .safeField("denialReason", denialReason)
                .safeField("actorRef", safeActorRef)
                .build());
    }
}
