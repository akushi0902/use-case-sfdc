package com.opsera.integrator.sfdc.command;

/**
 * Thrown when a downstream adapter handoff fails after a job was durably accepted.
 *
 * <p>When this exception is thrown the job has already been created in the lifecycle
 * store and transitioned to {@code FAILED} state with a safe diagnostic summary.
 * Callers may use {@link #getJobId()} to retrieve the failed job record.
 *
 * <p>Error messages never contain raw credentials, bearer tokens, stack traces from
 * internal systems, or customer-identifying data.
 */
public class CommandDispatchException extends RuntimeException {

    private final String jobId;
    private final String correlationId;
    private final String safeReasonCode;

    public CommandDispatchException(String jobId, String correlationId,
                                    String safeReasonCode, String safeMessage) {
        super(safeMessage);
        this.jobId = jobId;
        this.correlationId = correlationId;
        this.safeReasonCode = safeReasonCode;
    }

    public CommandDispatchException(String jobId, String correlationId,
                                    String safeReasonCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.jobId = jobId;
        this.correlationId = correlationId;
        this.safeReasonCode = safeReasonCode;
    }

    public String getJobId() { return jobId; }
    public String getCorrelationId() { return correlationId; }
    public String getSafeReasonCode() { return safeReasonCode; }
}
