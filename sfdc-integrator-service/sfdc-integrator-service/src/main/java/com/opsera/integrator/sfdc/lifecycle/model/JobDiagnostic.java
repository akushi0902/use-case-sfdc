package com.opsera.integrator.sfdc.lifecycle.model;

import java.time.Instant;

/**
 * Persistence model for the {@code job_diagnostics} table.
 *
 * <p>Stores a sanitized terminal diagnostic summary written when a job reaches
 * a terminal state. Raw stack traces, Salesforce CLI output, credentials,
 * tokens, and unrestricted exception messages are never stored here.
 * {@code safeSummary} is limited to 1024 characters.
 */
public class JobDiagnostic {

    private long id;
    private String jobId;
    /** Sanitized summary; max 1024 chars; no PII, no credentials, no stack trace. */
    private String safeSummary;
    private String errorCode;
    private Instant createdAt;

    public JobDiagnostic() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getSafeSummary() { return safeSummary; }
    public void setSafeSummary(String safeSummary) { this.safeSummary = safeSummary; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "JobDiagnostic{jobId='" + jobId + "', errorCode='" + errorCode + "'}";
    }
}
