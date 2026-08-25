package com.opsera.integrator.sfdc.observability;

import org.springframework.core.task.TaskDecorator;

/**
 * Spring {@link TaskDecorator} that propagates the MDC correlation identifier from the
 * submitting thread to the background worker thread.
 *
 * <p>Register this decorator on any {@code ThreadPoolTaskExecutor} to ensure that
 * CompletableFuture, Spring {@code @Async}, and explicit executor submissions all
 * carry the canonical correlation value established at HTTP ingress.
 *
 * <p>MDC is always cleared in a {@code finally} block after the task completes so that
 * pooled worker threads are not contaminated between jobs.
 */
public class CorrelationTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        return snapshot.wrap(runnable);
    }
}
