package com.opsera.integrator.sfdc.command;

/**
 * Accepts a validated release command, durably records an accepted job, and hands off
 * execution to a downstream adapter without blocking on long-running completion.
 *
 * <p>Callers receive an {@link AcceptedCommandOutcome} as soon as the job is durably persisted
 * and the adapter handoff is initiated — they must not assume execution has completed.
 *
 * <p>Lifecycle guarantees:
 * <ul>
 *   <li>If durable acceptance fails (repository or lifecycle service error), the dispatcher
 *       throws and does not invoke the adapter.</li>
 *   <li>If adapter handoff fails after job creation, the dispatcher marks the job failed and
 *       propagates the failure to the caller.</li>
 *   <li>Duplicate commands with the same idempotency key return the original accepted outcome
 *       without creating a second job record.</li>
 * </ul>
 */
public interface CommandDispatcher {

    /**
     * Dispatches the given command for asynchronous execution.
     *
     * @param command validated command metadata; must not be null
     * @return the accepted job outcome including job ID, correlation ID, status path, and state
     * @throws IllegalArgumentException if command is null or missing required fields
     * @throws com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException if durable acceptance fails
     * @throws CommandDispatchException if adapter handoff fails after the job was persisted
     */
    AcceptedCommandOutcome dispatch(AsyncCommandRequest command);
}
