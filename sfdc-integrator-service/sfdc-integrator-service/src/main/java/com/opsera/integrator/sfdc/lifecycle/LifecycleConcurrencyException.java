package com.opsera.integrator.sfdc.lifecycle;

/**
 * Thrown when an optimistic concurrency conflict is detected during a lifecycle
 * state update — another concurrent caller already updated the job record.
 *
 * <p>Callers should reload the job record and retry the transition if the retry
 * is still appropriate, or propagate the conflict to the caller for resolution.
 */
public class LifecycleConcurrencyException extends RuntimeException {

    private final String jobId;
    private final long expectedVersion;

    public LifecycleConcurrencyException(String jobId, long expectedVersion) {
        super("Optimistic concurrency conflict for job '" + jobId
                + "': expected version " + expectedVersion
                + " was not found — record was modified concurrently");
        this.jobId = jobId;
        this.expectedVersion = expectedVersion;
    }

    public String getJobId() { return jobId; }
    public long getExpectedVersion() { return expectedVersion; }
}
