package com.opsera.integrator.sfdc.exceptions;

/**
 * Thrown when a cancellation request is received for a job that is already in a
 * terminal state (COMPLETED, FAILED, CANCELLED, TIMED_OUT, REJECTED) and therefore
 * cannot be cancelled.
 *
 * <p>Maps to HTTP 409 Conflict via {@link SfdcExceptionHandler}.
 */
public class JobNotCancellableException extends RuntimeException {

    private final String jobId;
    private final String currentState;
    private final String correlationId;

    public JobNotCancellableException(String jobId, String currentState, String correlationId) {
        super("Job '" + jobId + "' is in terminal state '" + currentState
                + "' and cannot be cancelled");
        this.jobId = jobId;
        this.currentState = currentState;
        this.correlationId = correlationId;
    }

    public String getJobId() { return jobId; }
    public String getCurrentState() { return currentState; }
    public String getCorrelationId() { return correlationId; }
}
