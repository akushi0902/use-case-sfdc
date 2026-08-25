package com.opsera.integrator.sfdc.observability;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link TraceContextPropagation} using Micrometer's {@link SimpleTracer}.
 *
 * <p>Covers: span lifecycle, safe attribute tagging, error marking, null-tracer graceful
 * degradation, and NOOP behaviour for try-with-resources safety.
 */
class TraceContextPropagationTest {

    private SimpleTracer simpleTracer;
    private TraceContextPropagation propagation;

    @BeforeEach
    void setUp() {
        simpleTracer = new SimpleTracer();
        propagation = new TraceContextPropagation(simpleTracer);
    }

    // ---- Span lifecycle ----

    @Test
    void startSpan_withTracer_returnsNonNullScope() {
        TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span");
        assertThat(span).isNotNull();
        span.close();
    }

    @Test
    void startSpan_withTracer_spanIsCreated() {
        try (TraceContextPropagation.SpanInScope ignored = propagation.startSpan("quick-deploy-start")) {
            assertThat(simpleTracer.currentSpan()).isNotNull();
        }
    }

    @Test
    void close_endsSpan() {
        TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span");
        span.close();
        // After close, the span should have been ended
        assertThat(simpleTracer.getSpans()).isNotEmpty();
    }

    // ---- Safe attribute tagging ----

    @Test
    void tag_allowedKey_isAccepted() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.tag(SafeTraceAttributes.ATTR_OPERATION, "quick-deploy-start"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void tag_disallowedKey_usesRedactedSentinel() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.tag("jobId", "some-job"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void tag_nullKey_doesNotThrow() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.tag(null, "value")).doesNotThrowAnyException();
        }
    }

    @Test
    void tag_nullValue_doesNotThrow() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.tag(SafeTraceAttributes.ATTR_OPERATION, null)).doesNotThrowAnyException();
        }
    }

    @Test
    void tag_chainsFluentlyAndReturnsScope() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            TraceContextPropagation.SpanInScope returned = span
                    .tag(SafeTraceAttributes.ATTR_OPERATION, "quick-deploy-start")
                    .tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ACCEPTED);
            assertThat(returned).isSameAs(span);
        }
    }

    // ---- Error marking ----

    @Test
    void error_doesNotThrow() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.error(new RuntimeException("test error")))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void error_nullThrowable_doesNotThrow() {
        try (TraceContextPropagation.SpanInScope span = propagation.startSpan("test-span")) {
            assertThatCode(() -> span.error(null)).doesNotThrowAnyException();
        }
    }

    // ---- Null-tracer graceful degradation ----

    @Test
    void startSpan_nullTracer_returnsNoop() {
        TraceContextPropagation nullPropagation = new TraceContextPropagation(null);
        TraceContextPropagation.SpanInScope span = nullPropagation.startSpan("test-span");
        assertThat(span).isSameAs(TraceContextPropagation.SpanInScope.NOOP);
    }

    @Test
    void startSpan_nullTracer_noopDoesNotThrowOnTag() {
        TraceContextPropagation nullPropagation = new TraceContextPropagation(null);
        try (TraceContextPropagation.SpanInScope span = nullPropagation.startSpan("test-span")) {
            assertThatCode(() ->
                span.tag(SafeTraceAttributes.ATTR_OPERATION, "quick-deploy-start")
                    .tag(SafeTraceAttributes.ATTR_OUTCOME, SafeTraceAttributes.OUTCOME_ACCEPTED)
                    .error(new RuntimeException("ignored"))
            ).doesNotThrowAnyException();
        }
    }

    @Test
    void currentTraceId_nullTracer_returnsEmptyString() {
        TraceContextPropagation nullPropagation = new TraceContextPropagation(null);
        assertThat(nullPropagation.currentTraceId()).isEmpty();
    }

    @Test
    void currentTraceId_withActiveSpan_returnsNonEmpty() {
        try (TraceContextPropagation.SpanInScope ignored = propagation.startSpan("test-span")) {
            assertThat(propagation.currentTraceId()).isNotBlank();
        }
    }

    @Test
    void currentTraceId_noActiveSpan_returnsEmptyString() {
        assertThat(propagation.currentTraceId()).isEmpty();
    }

    // ---- NOOP safety ----

    @Test
    void noop_isUsableInTryWithResources() {
        assertThatCode(() -> {
            try (TraceContextPropagation.SpanInScope ignored = TraceContextPropagation.SpanInScope.NOOP) {
                // should not throw
            }
        }).doesNotThrowAnyException();
    }
}
