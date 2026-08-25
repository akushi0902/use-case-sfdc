package com.opsera.integrator.sfdc.observability;

import java.util.Set;

/**
 * Allow-list for span attribute keys used in release-critical flows.
 *
 * <p>Only the keys in {@link #ALLOWED_KEYS} may appear in spans. High-cardinality values
 * (jobId, customerId, pipelineId, raw correlation header content) are intentionally excluded
 * to prevent cardinality explosion in the tracing backend. Callers must use the constants
 * defined here rather than raw strings to prevent accidents.
 */
public final class SafeTraceAttributes {

    private SafeTraceAttributes() {}

    /** Sentinel key emitted when a caller supplies a disallowed attribute key. */
    public static final String REDACTED_KEY = "attribute.redacted";

    // ---- Allowed attribute key names ----

    public static final String ATTR_OPERATION       = "operation";
    public static final String ATTR_LIFECYCLE_PHASE = "lifecycle.phase";
    public static final String ATTR_OUTCOME         = "outcome";
    public static final String ATTR_HTTP_ROUTE      = "http.route";
    public static final String ATTR_CORRELATION_ID  = "correlation.id";
    public static final String ATTR_ERROR_TYPE      = "error.type";

    /** Bounded set of allowed span attribute key names. */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            ATTR_OPERATION,
            ATTR_LIFECYCLE_PHASE,
            ATTR_OUTCOME,
            ATTR_HTTP_ROUTE,
            ATTR_CORRELATION_ID,
            ATTR_ERROR_TYPE
    );

    // ---- Bounded operation values ----

    public static final String OP_QUICK_DEPLOY_START = "quick-deploy-start";
    public static final String OP_QUICK_DEPLOY_STOP  = "quick-deploy-stop";

    // ---- Bounded lifecycle phase values ----

    public static final String PHASE_REQUEST_RECEIVED = "request-received";
    public static final String PHASE_VALIDATION       = "validation";
    public static final String PHASE_DISPATCH         = "dispatch";
    public static final String PHASE_CANCELLATION     = "cancellation";
    public static final String PHASE_EXCEPTION        = "exception";

    // ---- Bounded outcome values ----

    public static final String OUTCOME_ACCEPTED = "accepted";
    public static final String OUTCOME_REJECTED = "rejected";
    public static final String OUTCOME_ERROR    = "error";

    // ---- Bounded HTTP route values ----

    public static final String ROUTE_QUICK_DEPLOY      = "/quickdeploy";
    public static final String ROUTE_QUICK_DEPLOY_STOP = "/quickdeploy/stop";

    /**
     * Returns the key unchanged when it is on the allow-list; returns {@link #REDACTED_KEY}
     * otherwise. Prevents high-cardinality keys from reaching the tracing backend.
     */
    public static String validate(String key) {
        if (key == null || !ALLOWED_KEYS.contains(key)) {
            return REDACTED_KEY;
        }
        return key;
    }
}
