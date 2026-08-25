package com.opsera.integrator.sfdc.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around Micrometer Tracing that provides span lifecycle management
 * for release-critical flows.
 *
 * <p>Gracefully handles a missing or unconfigured {@link Tracer} — when tracing is
 * not available (e.g. no OTLP endpoint configured, test profile), all methods return
 * safely without throwing. This ensures tracing failures never block release operations.
 *
 * <p>Span attributes are validated against {@link SafeTraceAttributes#ALLOWED_KEYS}
 * before being written to the span. Disallowed keys are replaced with
 * {@link SafeTraceAttributes#REDACTED_KEY} so that high-cardinality data cannot
 * accidentally reach the tracing backend.
 */
@Component
public class TraceContextPropagation {

    private final Tracer tracer;

    public TraceContextPropagation(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Starts a new child span with the given name and makes it the current span.
     *
     * <p>Returns a {@link SpanInScope} that must be closed (try-with-resources) to end
     * the span and restore the previous scope. If the tracer is unavailable, returns a
     * no-op that does nothing on close.
     *
     * @param name bounded span name — callers should use constants from {@link SafeTraceAttributes}
     * @return an {@link AutoCloseable} scope wrapping the active span
     */
    public SpanInScope startSpan(String name) {
        if (tracer == null) {
            return SpanInScope.NOOP;
        }
        try {
            Span span = tracer.nextSpan().name(name).start();
            Tracer.SpanInScope scope = tracer.withSpan(span);
            return new SpanInScope(span, scope);
        } catch (Exception e) {
            return SpanInScope.NOOP;
        }
    }

    /**
     * Returns the trace identifier of the current span, or an empty string when tracing
     * is unavailable or no span is active. Safe for inclusion in structured logs.
     */
    public String currentTraceId() {
        if (tracer == null) {
            return "";
        }
        try {
            Span current = tracer.currentSpan();
            return current != null ? current.context().traceId() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * A span and its enclosing scope, designed for try-with-resources usage.
     *
     * <p>Closing the scope ends the span. All {@code tag} and {@code error} calls
     * are no-ops when tracing is unavailable (i.e. when this is {@link #NOOP}).
     */
    public static final class SpanInScope implements AutoCloseable {

        public static final SpanInScope NOOP = new SpanInScope(null, null);

        private final Span span;
        private final Tracer.SpanInScope scope;

        private SpanInScope(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        /**
         * Attaches a safe span attribute. The key is validated against the allow-list;
         * disallowed keys are replaced with {@link SafeTraceAttributes#REDACTED_KEY}.
         * Neither key nor value may be null — silently ignored if either is null.
         */
        public SpanInScope tag(String key, String value) {
            if (span != null && key != null && value != null) {
                span.tag(SafeTraceAttributes.validate(key), value);
            }
            return this;
        }

        /** Marks the span as having encountered an error. No-op when tracing is unavailable. */
        public SpanInScope error(Throwable throwable) {
            if (span != null && throwable != null) {
                span.error(throwable);
            }
            return this;
        }

        @Override
        public void close() {
            try {
                if (scope != null) {
                    scope.close();
                }
            } finally {
                if (span != null) {
                    span.end();
                }
            }
        }
    }
}
