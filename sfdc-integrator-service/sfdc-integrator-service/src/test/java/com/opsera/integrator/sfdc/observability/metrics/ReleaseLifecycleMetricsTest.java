package com.opsera.integrator.sfdc.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReleaseLifecycleMetrics — unit tests")
class ReleaseLifecycleMetricsTest {

    private SimpleMeterRegistry registry;
    private ReleaseLifecycleMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ReleaseLifecycleMetrics(registry);
    }

    // ── Active workflow gauge ──────────────────────────────────────────────────

    @Test
    @DisplayName("gauge starts at zero")
    void activeWorkflows_initiallyZero() {
        assertThat(registry.get(ReleaseLifecycleMetrics.ACTIVE_WORKFLOWS).gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("incrementActiveWorkflows increments gauge")
    void activeWorkflows_incrementUpdatesGauge() {
        metrics.incrementActiveWorkflows();
        metrics.incrementActiveWorkflows();
        assertThat(metrics.getActiveWorkflowCount()).isEqualTo(2L);
        assertThat(registry.get(ReleaseLifecycleMetrics.ACTIVE_WORKFLOWS).gauge().value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("decrementActiveWorkflows decrements gauge")
    void activeWorkflows_decrementUpdatesGauge() {
        metrics.incrementActiveWorkflows();
        metrics.incrementActiveWorkflows();
        metrics.decrementActiveWorkflows();
        assertThat(metrics.getActiveWorkflowCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("decrementActiveWorkflows does not go below zero")
    void activeWorkflows_decrementFloorIsZero() {
        metrics.decrementActiveWorkflows();
        assertThat(metrics.getActiveWorkflowCount()).isEqualTo(0L);
    }

    // ── Acceptance attempt counter ─────────────────────────────────────────────

    @Test
    @DisplayName("recordAcceptanceAttempt registers counter with bounded tags")
    void recordAcceptanceAttempt_registersCounterAndIncrements() {
        metrics.recordAcceptanceAttempt(ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED);

        Counter counter = registry.get(ReleaseLifecycleMetrics.ACCEPTANCE_ATTEMPTS)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .tag(ReleaseLifecycleMetrics.TAG_OUTCOME, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordAcceptanceAttempt with null operation uses fallback tag")
    void recordAcceptanceAttempt_nullOperationUsesFallback() {
        metrics.recordAcceptanceAttempt(null, ReleaseLifecycleMetrics.OUTCOME_REJECTED);

        Counter counter = registry.get(ReleaseLifecycleMetrics.ACCEPTANCE_ATTEMPTS)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .tag(ReleaseLifecycleMetrics.TAG_OUTCOME, ReleaseLifecycleMetrics.OUTCOME_REJECTED)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Validation rejection counter ──────────────────────────────────────────

    @Test
    @DisplayName("recordValidationRejection registers counter")
    void recordValidationRejection_registersCounter() {
        metrics.recordValidationRejection(ReleaseLifecycleMetrics.OP_QUICK_DEPLOY);

        Counter counter = registry.get(ReleaseLifecycleMetrics.VALIDATION_REJECTIONS)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Cancellation attempt counter ──────────────────────────────────────────

    @Test
    @DisplayName("recordCancellationAttempt registers counter with outcome tag")
    void recordCancellationAttempt_registersCounterWithOutcomeTag() {
        metrics.recordCancellationAttempt(ReleaseLifecycleMetrics.OUTCOME_CANCELLED);

        Counter counter = registry.get(ReleaseLifecycleMetrics.CANCELLATION_ATTEMPTS)
                .tag(ReleaseLifecycleMetrics.TAG_OUTCOME, ReleaseLifecycleMetrics.OUTCOME_CANCELLED)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Dispatch failure counter ───────────────────────────────────────────────

    @Test
    @DisplayName("recordDispatchFailure registers counter with exception_category tag")
    void recordDispatchFailure_registersCounterWithExceptionCategory() {
        metrics.recordDispatchFailure(ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.EX_CATEGORY_DISPATCH);

        Counter counter = registry.get(ReleaseLifecycleMetrics.DISPATCH_FAILURES)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .tag(ReleaseLifecycleMetrics.TAG_EXCEPTION_CATEGORY, ReleaseLifecycleMetrics.EX_CATEGORY_DISPATCH)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ── Execution duration timer ───────────────────────────────────────────────

    @Test
    @DisplayName("recordExecutionDuration registers timer and records the duration")
    void recordExecutionDuration_registersTimerWithDuration() {
        long nanos = TimeUnit.MILLISECONDS.toNanos(250);
        metrics.recordExecutionDuration(nanos, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED);

        Timer timer = registry.get(ReleaseLifecycleMetrics.EXECUTION_DURATION)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .tag(ReleaseLifecycleMetrics.TAG_OUTCOME, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED)
                .timer();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(nanos);
    }

    @Test
    @DisplayName("recordExecutionDuration with null outcome uses fallback tag")
    void recordExecutionDuration_nullOutcomeUsesFallback() {
        metrics.recordExecutionDuration(1_000_000L, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, null);

        Timer timer = registry.get(ReleaseLifecycleMetrics.EXECUTION_DURATION)
                .tag(ReleaseLifecycleMetrics.TAG_OPERATION, ReleaseLifecycleMetrics.OP_QUICK_DEPLOY)
                .tag(ReleaseLifecycleMetrics.TAG_OUTCOME, "unknown")
                .timer();
        assertThat(timer.count()).isEqualTo(1L);
    }
}
