package com.opsera.integrator.sfdc.services.v2;

import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;

import java.util.Optional;

/**
 * Adapter boundary for v2 job status queries.
 *
 * <p>Isolates the status controller from the underlying status source so that
 * a future durable PostgreSQL lifecycle service can replace the current
 * implementation without changing the public v2 status contract or controller logic.
 *
 * <p>Current implementation: {@link LifecycleJobStatusAdapter} — reads from the
 * durable job repository (PostgreSQL-backed lifecycle storage from WO-119).
 *
 * <p>Contract:
 * <ul>
 *   <li>Returns a non-empty {@link Optional} with a fully-populated
 *       {@link ReleaseJobStatusResponse} when the job is found.</li>
 *   <li>Returns empty when the job ID is well-formed but not found in the status source.</li>
 *   <li>Never exposes raw payloads, credentials, stack traces, or unrestricted artifact paths
 *       in the response.</li>
 *   <li>Throws {@link com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException}
 *       on status source connectivity failures — the controller maps this to a safe 503.</li>
 * </ul>
 */
public interface JobStatusAdapter {

    /**
     * Returns the status for the given job ID, or empty if not found.
     *
     * @param jobId the validated (non-blank, safe-pattern) job identifier
     * @return the typed status response, or empty if the job is unknown
     */
    Optional<ReleaseJobStatusResponse> findByJobId(String jobId);
}
