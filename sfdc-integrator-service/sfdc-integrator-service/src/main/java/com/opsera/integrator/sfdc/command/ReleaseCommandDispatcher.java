package com.opsera.integrator.sfdc.command;

/**
 * Routes release commands to either Kubernetes worker execution or the legacy service path,
 * based on per-command-type configuration in {@link WorkerDispatchProperties}.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Never expose raw request DTOs, credentials, or org payloads in logs.</li>
 *   <li>Categorize failures as: routing disabled, worker unavailable, worker rejected,
 *       or legacy fallback failed — not silent swallows.</li>
 *   <li>Preserve legacy acknowledgement behavior when worker dispatch is disabled or falls back.</li>
 * </ul>
 */
public interface ReleaseCommandDispatcher {

    /**
     * Dispatches a release command for asynchronous execution.
     *
     * @param request validated dispatch request; must not be null
     * @return result including job ID, correlation ID, routing decision, and status path
     * @throws IllegalArgumentException if request is null or missing required fields
     * @throws CommandDispatchException if the selected execution path fails and no fallback is available
     */
    ReleaseDispatchResult dispatch(ReleaseDispatchRequest request);

    /**
     * Dispatches a cancellation request to the appropriate execution path.
     *
     * <p>Uses the same routing decision as {@link #dispatch}: if worker dispatch is enabled for
     * the command type, the cancellation targets the worker path; otherwise the legacy path is used.
     *
     * @param request cancel request carrying the command type, correlation ID, and safe identity fields
     * @return result confirming the cancellation was accepted and routed
     * @throws IllegalArgumentException if request is null or missing required fields
     * @throws CommandDispatchException if cancellation routing fails
     */
    ReleaseDispatchResult cancel(ReleaseDispatchRequest request);
}
