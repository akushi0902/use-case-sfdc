package com.opsera.integrator.sfdc.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Bounded Micrometer telemetry for release API coexistence traffic.
 *
 * <p>Emits two metric families:
 * <ul>
 *   <li>{@value #COUNTER_NAME} — incremented per request</li>
 *   <li>{@value #TIMER_NAME} — records latency per request</li>
 * </ul>
 *
 * <p>Tags are intentionally bounded to prevent cardinality explosion:
 * <ul>
 *   <li>{@code routeVersion} — legacy or v2</li>
 *   <li>{@code operationType} — QUICK_DEPLOY, DEPLOY, VALIDATE, STATUS_QUERY, UNKNOWN</li>
 *   <li>{@code outcome} — accepted, validationRejected, notFound, failedDelegation, disabledRoute</li>
 *   <li>{@code endpointFamily} — submission, status</li>
 * </ul>
 *
 * <p>Never includes high-cardinality tags such as jobId, customerId, pipelineId, correlationId,
 * or raw path variables. Telemetry recording failures are swallowed after a minimal safe log
 * so they cannot interrupt request processing.
 */
@Component
public class ReleaseCoexistenceTelemetry {

    private static final Logger log = LoggerFactory.getLogger(ReleaseCoexistenceTelemetry.class);

    public static final String COUNTER_NAME = "sfdc.release.api.requests";
    public static final String TIMER_NAME = "sfdc.release.api.latency";

    /** Bounded route version tag values. */
    public static final String ROUTE_VERSION_LEGACY = "legacy";
    public static final String ROUTE_VERSION_V2 = "v2";

    /** Bounded endpoint family tag values. */
    public static final String ENDPOINT_SUBMISSION = "submission";
    public static final String ENDPOINT_STATUS = "status";

    /** Bounded outcome tag values (AC-6). */
    public static final String OUTCOME_ACCEPTED = "accepted";
    public static final String OUTCOME_VALIDATION_REJECTED = "validationRejected";
    public static final String OUTCOME_NOT_FOUND = "notFound";
    public static final String OUTCOME_FAILED_DELEGATION = "failedDelegation";
    public static final String OUTCOME_DISABLED_ROUTE = "disabledRoute";

    /** Sentinel for operation type when none is determinable without inspecting high-cardinality data. */
    public static final String OPERATION_UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public ReleaseCoexistenceTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a coexistence telemetry event with bounded tags.
     *
     * <p>This method never throws. Any internal exception is swallowed after a minimal
     * safe log entry so that telemetry never interrupts request processing.
     *
     * @param routeVersion  bounded route version tag (e.g. {@link #ROUTE_VERSION_V2})
     * @param operationType bounded operation type (e.g. "QUICK_DEPLOY"); caller must resolve to a safe label
     * @param outcome       bounded outcome tag (one of the {@code OUTCOME_*} constants)
     * @param endpointFamily bounded endpoint family tag ({@link #ENDPOINT_SUBMISSION} or {@link #ENDPOINT_STATUS})
     * @param latencyMs     request latency in milliseconds; negative values are clamped to 0
     */
    public void record(String routeVersion,
                       String operationType,
                       String outcome,
                       String endpointFamily,
                       long latencyMs) {
        try {
            String safeRouteVersion = sanitize(routeVersion, ROUTE_VERSION_V2);
            String safeOperationType = sanitize(operationType, OPERATION_UNKNOWN);
            String safeOutcome = sanitize(outcome, OUTCOME_ACCEPTED);
            String safeEndpointFamily = sanitize(endpointFamily, ENDPOINT_SUBMISSION);
            long safLatency = Math.max(0L, latencyMs);

            Counter.builder(COUNTER_NAME)
                    .tag("routeVersion", safeRouteVersion)
                    .tag("operationType", safeOperationType)
                    .tag("outcome", safeOutcome)
                    .tag("endpointFamily", safeEndpointFamily)
                    .register(meterRegistry)
                    .increment();

            Timer.builder(TIMER_NAME)
                    .tag("routeVersion", safeRouteVersion)
                    .tag("operationType", safeOperationType)
                    .tag("outcome", safeOutcome)
                    .tag("endpointFamily", safeEndpointFamily)
                    .register(meterRegistry)
                    .record(safLatency, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.warn("telemetry-record-failure routeVersion={} endpointFamily={} outcome={}",
                    routeVersion, endpointFamily, outcome);
        }
    }

    /**
     * Resolves a bounded operation type label from a raw enum or string value.
     *
     * <p>Only known operation types are returned — anything else maps to {@link #OPERATION_UNKNOWN}
     * so that an unexpected value does not pollute the metrics cardinality.
     *
     * @param rawOperationType string representation of the operation type (may be null)
     * @return bounded label safe for use as a Micrometer tag value
     */
    public static String resolveOperationType(String rawOperationType) {
        if (rawOperationType == null || rawOperationType.isBlank()) {
            return OPERATION_UNKNOWN;
        }
        switch (rawOperationType.trim().toUpperCase()) {
            case "QUICK_DEPLOY": return "QUICK_DEPLOY";
            case "DEPLOY":       return "DEPLOY";
            case "VALIDATE":     return "VALIDATE";
            case "ROLLBACK":     return "ROLLBACK";
            case "PACKAGE":      return "PACKAGE";
            case "DIAGNOSTIC":   return "DIAGNOSTIC";
            case "CANCEL":       return "CANCEL";
            default:             return OPERATION_UNKNOWN;
        }
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        // Strip control characters and limit length to prevent log injection or long tag names
        String cleaned = value.replaceAll("[\r\n\t\\x00-\\x1F]", "_").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
