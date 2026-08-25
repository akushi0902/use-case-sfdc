package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobDiagnostic;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.lifecycle.model.WorkerAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed implementation of {@link JobRepository}.
 *
 * <p>Uses Spring {@link JdbcTemplate} for all data access. SQL is kept simple
 * and vendor-compatible for PostgreSQL (production) and H2 PostgreSQL mode (test).
 *
 * <p>State transitions use a version check (optimistic concurrency): if no row
 * is updated, the caller supplied a stale version and a
 * {@link LifecyclePersistenceException} is thrown.
 */
@Repository
public class JdbcJobRepository implements JobRepository {

    private final JdbcTemplate jdbc;

    public JdbcJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(JobRecord job) {
        try {
            jdbc.update(
                    "INSERT INTO jobs (job_id, correlation_id, operation_type, current_state, " +
                    "customer_id_hash, sfdc_tool_id, pipeline_id, step_id, data_classification, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    job.getJobId(), job.getCorrelationId(), job.getOperationType(),
                    job.getCurrentState(), job.getCustomerIdHash(), job.getSfdcToolId(),
                    job.getPipelineId(), job.getStepId(),
                    job.getDataClassification() != null ? job.getDataClassification() : "CONFIDENTIAL",
                    job.getVersion());
        } catch (Exception ex) {
            throw new LifecyclePersistenceException(
                    "Failed to save job: correlationId=" + job.getCorrelationId(), ex);
        }
    }

    @Override
    public Optional<JobRecord> findById(String jobId) {
        List<JobRecord> results = jdbc.query(
                "SELECT * FROM jobs WHERE job_id = ?",
                JOB_ROW_MAPPER, jobId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<JobRecord> findByCorrelationId(String correlationId) {
        List<JobRecord> results = jdbc.query(
                "SELECT * FROM jobs WHERE correlation_id = ? ORDER BY created_at DESC LIMIT 1",
                JOB_ROW_MAPPER, correlationId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void updateState(String jobId, String newState, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE jobs SET current_state = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE job_id = ? AND version = ?",
                newState, jobId, expectedVersion);
        if (updated == 0) {
            throw new LifecyclePersistenceException(
                    "State update failed — version mismatch or job not found: jobId=" + jobId +
                    " expectedVersion=" + expectedVersion);
        }
    }

    @Override
    public void saveCheckpoint(JobCheckpoint checkpoint) {
        try {
            jdbc.update(
                    "INSERT INTO job_checkpoints (job_id, sequence_number, checkpoint_code, " +
                    "state_at_checkpoint, safe_message) VALUES (?, ?, ?, ?, ?)",
                    checkpoint.getJobId(), checkpoint.getSequenceNumber(),
                    checkpoint.getCheckpointCode(), checkpoint.getStateAtCheckpoint(),
                    checkpoint.getSafeMessage());
        } catch (Exception ex) {
            throw new LifecyclePersistenceException(
                    "Failed to save checkpoint: jobId=" + checkpoint.getJobId() +
                    " seq=" + checkpoint.getSequenceNumber(), ex);
        }
    }

    @Override
    public List<JobCheckpoint> findCheckpointsByJobId(String jobId) {
        return jdbc.query(
                "SELECT * FROM job_checkpoints WHERE job_id = ? ORDER BY sequence_number ASC",
                CHECKPOINT_ROW_MAPPER, jobId);
    }

    @Override
    public void saveDiagnostic(JobDiagnostic diagnostic) {
        try {
            jdbc.update(
                    "INSERT INTO job_diagnostics (job_id, safe_summary, error_code) VALUES (?, ?, ?)",
                    diagnostic.getJobId(), diagnostic.getSafeSummary(), diagnostic.getErrorCode());
        } catch (Exception ex) {
            throw new LifecyclePersistenceException(
                    "Failed to save diagnostic: jobId=" + diagnostic.getJobId(), ex);
        }
    }

    @Override
    public void saveWorkerAttempt(WorkerAttempt attempt) {
        try {
            jdbc.update(
                    "INSERT INTO worker_attempts (job_id, attempt_number, worker_id, started_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    attempt.getJobId(), attempt.getAttemptNumber(), attempt.getWorkerId());
        } catch (Exception ex) {
            throw new LifecyclePersistenceException(
                    "Failed to save worker attempt: jobId=" + attempt.getJobId() +
                    " attempt=" + attempt.getAttemptNumber(), ex);
        }
    }

    // ---- Row mappers ----

    private static final RowMapper<JobRecord> JOB_ROW_MAPPER = (rs, rowNum) -> {
        JobRecord job = new JobRecord();
        job.setJobId(rs.getString("job_id"));
        job.setCorrelationId(rs.getString("correlation_id"));
        job.setOperationType(rs.getString("operation_type"));
        job.setCurrentState(rs.getString("current_state"));
        job.setCustomerIdHash(rs.getString("customer_id_hash"));
        job.setSfdcToolId(rs.getString("sfdc_tool_id"));
        job.setPipelineId(rs.getString("pipeline_id"));
        job.setStepId(rs.getString("step_id"));
        job.setDataClassification(rs.getString("data_classification"));
        job.setVersion(rs.getLong("version"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) job.setCreatedAt(created.toInstant());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) job.setUpdatedAt(updated.toInstant());
        Timestamp expires = rs.getTimestamp("expires_at");
        if (expires != null) job.setExpiresAt(expires.toInstant());
        return job;
    };

    private static final RowMapper<JobCheckpoint> CHECKPOINT_ROW_MAPPER = (rs, rowNum) -> {
        JobCheckpoint cp = new JobCheckpoint();
        cp.setId(rs.getLong("id"));
        cp.setJobId(rs.getString("job_id"));
        cp.setSequenceNumber(rs.getInt("sequence_number"));
        cp.setCheckpointCode(rs.getString("checkpoint_code"));
        cp.setStateAtCheckpoint(rs.getString("state_at_checkpoint"));
        cp.setSafeMessage(rs.getString("safe_message"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) cp.setCreatedAt(created.toInstant());
        return cp;
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
