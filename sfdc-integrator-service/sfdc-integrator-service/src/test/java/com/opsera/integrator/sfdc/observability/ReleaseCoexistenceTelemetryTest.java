package com.opsera.integrator.sfdc.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link ReleaseCoexistenceTelemetry}.
 *
 * <p>Uses {@link SimpleMeterRegistry} (in-memory) — no Spring context required.
 */
@DisplayName("ReleaseCoexistenceTelemetry — unit tests")
class ReleaseCoexistenceTelemetryTest {

    private MeterRegistry meterRegistry;
    private ReleaseCoexistenceTelemetry telemetry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        telemetry = new ReleaseCoexistenceTelemetry(meterRegistry);
    }

    // ── Counter registration ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Counter registration and tag cardinality")
    class CounterRegistration {

        @Test
        @DisplayName("record() increments the request counter with expected bounded tags")
        void record_incremetsCounterWithBoundedTags() {
            telemetry.record(ROUTE_VERSION_V2, "QUICK_DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 50L);

            Counter counter = meterRegistry.find(COUNTER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_V2)
                    .tag("operationType", "QUICK_DEPLOY")
                    .tag("outcome", OUTCOME_ACCEPTED)
                    .tag("endpointFamily", ENDPOINT_SUBMISSION)
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("record() increments the same counter on repeated calls")
        void record_multipleCallsSameTagSet_accumulates() {
            telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 10L);
            telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 20L);
            telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 30L);

            Counter counter = meterRegistry.find(COUNTER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_V2)
                    .tag("operationType", "DEPLOY")
                    .tag("outcome", OUTCOME_ACCEPTED)
                    .tag("endpointFamily", ENDPOINT_SUBMISSION)
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("record() creates distinct counters for distinct outcome tag values")
        void record_distinctOutcomes_distinctCounters() {
            telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 10L);
            telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_NOT_FOUND, ENDPOINT_SUBMISSION, 5L);

            Counter accepted = meterRegistry.find(COUNTER_NAME)
                    .tag("outcome", OUTCOME_ACCEPTED).counter();
            Counter notFound = meterRegistry.find(COUNTER_NAME)
                    .tag("outcome", OUTCOME_NOT_FOUND).counter();

            assertThat(accepted).isNotNull();
            assertThat(notFound).isNotNull();
            assertThat(accepted.count()).isEqualTo(1.0);
            assertThat(notFound.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("record() creates distinct counters for legacy vs v2 routeVersion")
        void record_distinctRouteVersions_distinctCounters() {
            telemetry.record(ROUTE_VERSION_LEGACY, "QUICK_DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 10L);
            telemetry.record(ROUTE_VERSION_V2, "QUICK_DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 10L);

            Counter legacyCounter = meterRegistry.find(COUNTER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_LEGACY).counter();
            Counter v2Counter = meterRegistry.find(COUNTER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_V2).counter();

            assertThat(legacyCounter).isNotNull();
            assertThat(v2Counter).isNotNull();
            assertThat(legacyCounter.count()).isEqualTo(1.0);
            assertThat(v2Counter.count()).isEqualTo(1.0);
        }
    }

    // ── Timer registration ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timer registration")
    class TimerRegistration {

        @Test
        @DisplayName("record() registers a timer with the expected tag set")
        void record_registersTimerWithTags() {
            telemetry.record(ROUTE_VERSION_V2, "VALIDATE", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, 75L);

            Timer timer = meterRegistry.find(TIMER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_V2)
                    .tag("operationType", "VALIDATE")
                    .tag("outcome", OUTCOME_ACCEPTED)
                    .tag("endpointFamily", ENDPOINT_SUBMISSION)
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("negative latencyMs is clamped to 0")
        void record_negativeLatency_clampedToZero() {
            assertThatCode(() -> telemetry.record(ROUTE_VERSION_V2, "DEPLOY", OUTCOME_ACCEPTED, ENDPOINT_SUBMISSION, -100L))
                    .doesNotThrowAnyException();

            Timer timer = meterRegistry.find(TIMER_NAME)
                    .tag("routeVersion", ROUTE_VERSION_V2)
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }
    }

    // ── Bounded operation type resolution ─────────────────────────────────────

    @Nested
    @DisplayName("Bounded operation type resolution")
    class OperationTypeResolution {

        @Test void resolveOperationType_quickDeploy()  { assertThat(resolveOperationType("QUICK_DEPLOY")).isEqualTo("QUICK_DEPLOY"); }
        @Test void resolveOperationType_deploy()       { assertThat(resolveOperationType("DEPLOY")).isEqualTo("DEPLOY"); }
        @Test void resolveOperationType_validate()     { assertThat(resolveOperationType("VALIDATE")).isEqualTo("VALIDATE"); }
        @Test void resolveOperationType_rollback()     { assertThat(resolveOperationType("ROLLBACK")).isEqualTo("ROLLBACK"); }
        @Test void resolveOperationType_cancel()       { assertThat(resolveOperationType("CANCEL")).isEqualTo("CANCEL"); }
        @Test void resolveOperationType_package()      { assertThat(resolveOperationType("PACKAGE")).isEqualTo("PACKAGE"); }
        @Test void resolveOperationType_diagnostic()   { assertThat(resolveOperationType("DIAGNOSTIC")).isEqualTo("DIAGNOSTIC"); }

        @Test
        @DisplayName("null operationType resolves to UNKNOWN")
        void resolveOperationType_null_returnsUnknown() {
            assertThat(resolveOperationType(null)).isEqualTo(OPERATION_UNKNOWN);
        }

        @Test
        @DisplayName("blank operationType resolves to UNKNOWN")
        void resolveOperationType_blank_returnsUnknown() {
            assertThat(resolveOperationType("   ")).isEqualTo(OPERATION_UNKNOWN);
        }

        @Test
        @DisplayName("unrecognized operationType resolves to UNKNOWN (prevents high-cardinality tag)")
        void resolveOperationType_unrecognized_returnsUnknown() {
            assertThat(resolveOperationType("SOME_FUTURE_OPERATION_TYPE")).isEqualTo(OPERATION_UNKNOWN);
        }

        @Test
        @DisplayName("case-insensitive: lowercase resolves to canonical label")
        void resolveOperationType_lowerCase_resolves() {
            assertThat(resolveOperationType("quick_deploy")).isEqualTo("QUICK_DEPLOY");
        }
    }

    // ── Fail-safe behavior ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fail-safe behavior")
    class FailSafe {

        @Test
        @DisplayName("null tag values fall back to defaults — record() never throws")
        void record_nullTagValues_doesNotThrow() {
            assertThatCode(() -> telemetry.record(null, null, null, null, 10L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("blank tag values fall back to defaults — record() never throws")
        void record_blankTagValues_doesNotThrow() {
            assertThatCode(() -> telemetry.record("", "  ", "", "  ", 10L))
                    .doesNotThrowAnyException();
        }
    }

    // ── High-cardinality tag prevention ───────────────────────────────────────

    @Nested
    @DisplayName("High-cardinality tag prevention")
    class HighCardinalityPrevention {

        @Test
        @DisplayName("job identifier injected as operationType is remapped to UNKNOWN")
        void record_jobIdAsOperationType_mapsToUnknown() {
            String jobLikeValue = "job-a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            String resolved = resolveOperationType(jobLikeValue);
            assertThat(resolved).isEqualTo(OPERATION_UNKNOWN);
        }

        @Test
        @DisplayName("customer identifier injected as operationType is remapped to UNKNOWN")
        void record_customerIdAsOperationType_mapsToUnknown() {
            String customerLikeValue = "customer-opsera-prod-001";
            String resolved = resolveOperationType(customerLikeValue);
            assertThat(resolved).isEqualTo(OPERATION_UNKNOWN);
        }
    }
}
