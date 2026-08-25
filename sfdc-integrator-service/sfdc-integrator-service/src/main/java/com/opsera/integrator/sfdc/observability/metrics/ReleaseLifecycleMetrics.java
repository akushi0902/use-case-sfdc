package com.opsera.integrator.sfdc.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics recorder for release lifecycle workflows.
 *
 * <p>Emits bounded counters, timers, and gauges for the quick-deploy path and related
 * release operations. All tag values are bounded to a fixed set — no customer identifiers,
 * Salesforce org identifiers, job identifiers, tokens, or raw request payloads are used
 * as tag values.
 *
 * <p>Metric recording failures are swallowed after a minimal safe log entry so they
 * cannot interrupt release workflow execution.
 *
 * <h2>Metric families</h2>
 * <ul>
 *   <li>{@value #ACCEPTANCE_ATTEMPTS} — counter, tags: operation, outcome</li>
 *   <li>{@value #VALIDATION_REJECTIONS} — counter, tags: operation</li>
 *   <li>{@value #CANCELLATION_ATTEMPTS} — counter, tags: outcome</li>
 *   <li>{@value #DISPATCH_FAILURES} — counter, tags: operation, exception_category</li>
 *   <li>{@value #EXECUTION_DURATION} — timer, tags: operation, outcome</li>
 *   <li>{@value #ACTIVE_WORKFLOWS} — gauge, reports current active workflow count</li>
 * </ul>
 *
 * <h2>Bounded tag keys</h2>
 * operation, outcome, exception_category
 *
 * <h2>Bounded tag values</h2>
 * operation: QUICK_DEPLOY, QUICK_DEPLOY_STOP<br>
 * outcome: accepted, rejected, dispatched, failed, cancelled<br>
 * exception_category: validation, authorization, dispatch, unknown
 */
@Component
public class ReleaseLifecycleMetrics {

    private static final Logger log = LoggerFactory.getLogger(ReleaseLifecycleMetrics.class);

    // ---- Metric name constants ----

    public static final String ACCEPTANCE_ATTEMPTS  = "sfdc.release.acceptance.attempts";
    public static final String VALIDATION_REJECTIONS = "sfdc.release.validation.rejections";
    public static final String CANCELLATION_ATTEMPTS = "sfdc.release.cancellation.attempts";
    public static final String DISPATCH_FAILURES     = "sfdc.release.dispatch.failures";
    public static final String EXECUTION_DURATION    = "sfdc.release.execution.duration";
    public static final String ACTIVE_WORKFLOWS      = "sfdc.release.active.workflows";

    // ---- Bounded tag key constants ----

    public static final String TAG_OPERATION           = "operation";
    public static final String TAG_OUTCOME             = "outcome";
    public static final String TAG_EXCEPTION_CATEGORY  = "exception_category";

    // ---- Bounded operation tag values ----

    public static final String OP_QUICK_DEPLOY      = "QUICK_DEPLOY";
    public static final String OP_QUICK_DEPLOY_STOP = "QUICK_DEPLOY_STOP";

    // ---- Bounded outcome tag values ----

    public static final String OUTCOME_ACCEPTED   = "accepted";
    public static final String OUTCOME_REJECTED   = "rejected";
    public static final String OUTCOME_DISPATCHED = "dispatched";
    public static final String OUTCOME_FAILED     = "failed";
    public static final String OUTCOME_CANCELLED  = "cancelled";

    // ---- Bounded exception category tag values ----

    public static final String EX_CATEGORY_VALIDATION   = "validation";
    public static final String EX_CATEGORY_AUTHORIZATION = "authorization";
    public static final String EX_CATEGORY_DISPATCH      = "dispatch";
    public static final String EX_CATEGORY_UNKNOWN       = "unknown";

    private final MeterRegistry meterRegistry;
    private final AtomicLong activeWorkflowCount = new AtomicLong(0L);

    public ReleaseLifecycleMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge(ACTIVE_WORKFLOWS, activeWorkflowCount, AtomicLong::doubleValue);
    }

    /**
     * Increments the acceptance attempt counter for a release workflow.
     *
     * @param operation bounded operation name (e.g. {@link #OP_QUICK_DEPLOY})
     * @param outcome   bounded outcome (e.g. {@link #OUTCOME_ACCEPTED}, {@link #OUTCOME_REJECTED})
     */
    public void recordAcceptanceAttempt(String operation, String outcome) {
        try {
            Counter.builder(ACCEPTANCE_ATTEMPTS)
                    .tag(TAG_OPERATION, safe(operation, OP_QUICK_DEPLOY))
                    .tag(TAG_OUTCOME, safe(outcome, OUTCOME_UNKNOWN))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("metrics-record-failure metric={} operation={} outcome={}", ACCEPTANCE_ATTEMPTS, operation, outcome);
        }
    }

    /**
     * Increments the validation rejection counter.
     *
     * @param operation bounded operation name
     */
    public void recordValidationRejection(String operation) {
        try {
            Counter.builder(VALIDATION_REJECTIONS)
                    .tag(TAG_OPERATION, safe(operation, OP_QUICK_DEPLOY))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("metrics-record-failure metric={} operation={}", VALIDATION_REJECTIONS, operation);
        }
    }

    /**
     * Increments the cancellation attempt counter.
     *
     * @param outcome bounded outcome (accepted/rejected/failed)
     */
    public void recordCancellationAttempt(String outcome) {
        try {
            Counter.builder(CANCELLATION_ATTEMPTS)
                    .tag(TAG_OUTCOME, safe(outcome, OUTCOME_UNKNOWN))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("metrics-record-failure metric={} outcome={}", CANCELLATION_ATTEMPTS, outcome);
        }
    }

    /**
     * Increments the dispatch failure counter.
     *
     * @param operation         bounded operation name
     * @param exceptionCategory bounded exception category (e.g. {@link #EX_CATEGORY_DISPATCH})
     */
    public void recordDispatchFailure(String operation, String exceptionCategory) {
        try {
            Counter.builder(DISPATCH_FAILURES)
                    .tag(TAG_OPERATION, safe(operation, OP_QUICK_DEPLOY))
                    .tag(TAG_EXCEPTION_CATEGORY, safe(exceptionCategory, EX_CATEGORY_UNKNOWN))
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("metrics-record-failure metric={} operation={} exceptionCategory={}", DISPATCH_FAILURES, operation, exceptionCategory);
        }
    }

    /**
     * Records execution duration for a completed workflow operation.
     *
     * @param durationNanos elapsed time in nanoseconds
     * @param operation     bounded operation name
     * @param outcome       bounded outcome
     */
    public void recordExecutionDuration(long durationNanos, String operation, String outcome) {
        try {
            Timer.builder(EXECUTION_DURATION)
                    .tag(TAG_OPERATION, safe(operation, OP_QUICK_DEPLOY))
                    .tag(TAG_OUTCOME, safe(outcome, OUTCOME_UNKNOWN))
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("metrics-record-failure metric={} operation={} outcome={}", EXECUTION_DURATION, operation, outcome);
        }
    }

    /**
     * Increments the active workflow gauge by one.
     * Call when a release workflow is accepted and dispatched.
     */
    public void incrementActiveWorkflows() {
        activeWorkflowCount.incrementAndGet();
    }

    /**
     * Decrements the active workflow gauge by one (floor: 0).
     * Call when a release workflow reaches a terminal state.
     */
    public void decrementActiveWorkflows() {
        activeWorkflowCount.updateAndGet(v -> Math.max(0L, v - 1));
    }

    /** Returns the current active workflow gauge value — useful in tests. */
    long getActiveWorkflowCount() {
        return activeWorkflowCount.get();
    }

    private static final String OUTCOME_UNKNOWN = "unknown";

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
