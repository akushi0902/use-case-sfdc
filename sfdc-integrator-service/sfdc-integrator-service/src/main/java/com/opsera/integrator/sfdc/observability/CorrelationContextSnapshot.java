package com.opsera.integrator.sfdc.observability;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.slf4j.MDC;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Captures the active MDC correlation identifier at a point in time so it can be
 * safely restored on a different thread during async or Kafka listener execution.
 *
 * <p>Usage pattern:
 * <pre>{@code
 *   // On the original (servlet) thread:
 *   CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
 *
 *   // On the background thread:
 *   Runnable wrapped = snapshot.wrap(() -> { ... background work ... });
 *   wrapped.run();
 * }</pre>
 *
 * <p>The wrapped delegates always clear MDC in a {@code finally} block so pooled
 * worker threads are not contaminated across jobs.
 */
public final class CorrelationContextSnapshot {

    private final String correlationId;

    private CorrelationContextSnapshot(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * Captures the current MDC correlation identifier.
     * Returns a snapshot with an empty string if MDC has no value (background context).
     */
    public static CorrelationContextSnapshot capture() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return new CorrelationContextSnapshot(id != null ? id : "");
    }

    /**
     * Returns the captured correlation identifier (empty string when none was present).
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Wraps a {@link Runnable} so it executes with the captured correlation context restored
     * in MDC. MDC is restored to its state prior to wrapping after the delegate completes,
     * even if the delegate throws.
     */
    public Runnable wrap(Runnable delegate) {
        return () -> {
            String previous = MDC.get(CorrelationIdConstants.MDC_KEY);
            applyCorrelationId();
            try {
                delegate.run();
            } finally {
                restorePrevious(previous);
            }
        };
    }

    /**
     * Wraps a {@link Callable} so it executes with the captured correlation context restored
     * in MDC. MDC is restored after the callable completes or throws.
     */
    public <T> Callable<T> wrap(Callable<T> delegate) {
        return () -> {
            String previous = MDC.get(CorrelationIdConstants.MDC_KEY);
            applyCorrelationId();
            try {
                return delegate.call();
            } finally {
                restorePrevious(previous);
            }
        };
    }

    /**
     * Wraps a {@link Supplier} so it executes with the captured correlation context restored
     * in MDC. MDC is restored after the supplier completes or throws.
     */
    public <T> Supplier<T> wrap(Supplier<T> delegate) {
        return () -> {
            String previous = MDC.get(CorrelationIdConstants.MDC_KEY);
            applyCorrelationId();
            try {
                return delegate.get();
            } finally {
                restorePrevious(previous);
            }
        };
    }

    private void applyCorrelationId() {
        if (!correlationId.isBlank()) {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        } else {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }

    private void restorePrevious(String previous) {
        if (previous != null) {
            MDC.put(CorrelationIdConstants.MDC_KEY, previous);
        } else {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }
}
