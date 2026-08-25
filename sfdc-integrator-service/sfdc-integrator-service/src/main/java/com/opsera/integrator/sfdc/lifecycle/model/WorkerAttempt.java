package com.opsera.integrator.sfdc.lifecycle.model;

import java.time.Instant;

/**
 * Persistence model for the {@code worker_attempts} table.
 *
 * <p>Records each execution attempt for a job, supporting retry tracking and
 * timeout detection. {@code workerId} is an opaque internal identifier
 * (e.g. Kubernetes pod name) and must not include customer PII.
 */
public class WorkerAttempt {

    private long id;
    private String jobId;
    private int attemptNumber;
    private String workerId;
    private Instant startedAt;
    private Instant completedAt;
    /** SUCCEEDED | FAILED | TIMED_OUT | null (still in progress). */
    private String outcome;

    public WorkerAttempt() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    @Override
    public String toString() {
        return "WorkerAttempt{jobId='" + jobId + "', attempt=" + attemptNumber +
                ", outcome='" + outcome + "'}";
    }
}
