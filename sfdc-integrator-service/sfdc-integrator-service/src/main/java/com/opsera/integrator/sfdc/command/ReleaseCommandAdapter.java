package com.opsera.integrator.sfdc.command;

/**
 * Execution adapter interface for release operations.
 *
 * <p>Implementations translate an {@link AsyncCommandRequest} into downstream service calls
 * (quick deploy, full deploy, validate, etc.) without modifying internal service contracts.
 * Adapters must be non-blocking: they should enqueue work or submit to an executor rather
 * than waiting for Salesforce CLI or job-engine completion.
 *
 * <p>An adapter may throw a {@link RuntimeException} to signal that handoff initiation failed.
 * The {@link DefaultCommandDispatcher} will catch any adapter exception, mark the job failed
 * via the lifecycle service, and propagate a {@link CommandDispatchException} to the caller.
 */
public interface ReleaseCommandAdapter {

    /**
     * Returns the operation type string this adapter handles (e.g. "QUICK_DEPLOY").
     * Used by the dispatcher for routing when multiple adapters are registered.
     */
    String supportedOperationType();

    /**
     * Initiates execution of the given command without blocking on completion.
     *
     * @param command the accepted command to execute; never null
     * @throws RuntimeException if the initial handoff cannot be initiated
     */
    void execute(AsyncCommandRequest command);
}
