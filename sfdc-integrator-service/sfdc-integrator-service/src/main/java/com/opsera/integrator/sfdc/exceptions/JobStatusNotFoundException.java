package com.opsera.integrator.sfdc.exceptions;

/**
 * Thrown by the v2 status adapter when a job ID is well-formed but references no known job.
 *
 * <p>Results in HTTP 404 from {@link SfdcExceptionHandler}. The {@code jobId} is included
 * for structured logging and correlation but is not present in the response body.
 */
public class JobStatusNotFoundException extends RuntimeException {

    private final String jobId;
    private final String correlationId;

    public JobStatusNotFoundException(String jobId, String correlationId) {
        super("Job not found: " + jobId);
        this.jobId = jobId;
        this.correlationId = correlationId;
    }

    public String getJobId() { return jobId; }
    public String getCorrelationId() { return correlationId; }
}
