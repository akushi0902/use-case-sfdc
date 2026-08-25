package com.opsera.integrator.sfdc.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the stalled-job timeout monitor.
 *
 * <p>Bound from {@code sfdc.lifecycle.timeout.*} in application.yaml.
 *
 * <p>Tuning guidance:
 * <ul>
 *   <li>{@code stale-threshold-minutes} must be longer than the normal worker checkpoint
 *       interval (60 s) plus the expected maximum single-stage execution time. Default 240 minutes
 *       (4 hours) covers the longest known Salesforce full-org deployment window.</li>
 *   <li>{@code accepted-grace-period-minutes} protects freshly accepted jobs that have not yet
 *       reached a worker from premature timeout. Default 30 minutes allows for dispatcher
 *       startup and scheduling delays.</li>
 *   <li>{@code scan-interval-seconds} controls how often the monitor polls. Default 300 (5 min)
 *       limits database load while ensuring stale jobs are detected within one polling interval
 *       after the threshold elapses.</li>
 *   <li>{@code max-jobs-per-scan} caps the number of timeouts applied per run to bound the
 *       blast radius of a runaway scan.</li>
 * </ul>
 *
 * <p>To disable the monitor during emergency maintenance set {@code enabled=false}.
 */
@ConfigurationProperties(prefix = "sfdc.lifecycle.timeout")
public class JobTimeoutProperties {

    /** Enable or disable the timeout monitor. Safe default: enabled. */
    private boolean enabled = true;

    /**
     * Interval between monitor scans, in seconds.
     * This value is used as the {@code fixedDelay} for the scheduled monitor.
     */
    private long scanIntervalSeconds = 300L;

    /**
     * Jobs with no state change for longer than this threshold are considered stale.
     * Applies to DISPATCHING and RUNNING states.
     */
    private long staleThresholdMinutes = 240L;

    /**
     * Minimum time after acceptance before a job with no checkpoints can be timed out.
     * Protects freshly accepted jobs awaiting worker dispatch.
     */
    private long acceptedGracePeriodMinutes = 30L;

    /**
     * Maximum number of jobs to finalize per scan. Caps the blast radius of a single run.
     */
    private int maxJobsPerScan = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getScanIntervalSeconds() { return scanIntervalSeconds; }
    public void setScanIntervalSeconds(long scanIntervalSeconds) { this.scanIntervalSeconds = scanIntervalSeconds; }

    public long getStaleThresholdMinutes() { return staleThresholdMinutes; }
    public void setStaleThresholdMinutes(long staleThresholdMinutes) { this.staleThresholdMinutes = staleThresholdMinutes; }

    public long getAcceptedGracePeriodMinutes() { return acceptedGracePeriodMinutes; }
    public void setAcceptedGracePeriodMinutes(long acceptedGracePeriodMinutes) {
        this.acceptedGracePeriodMinutes = acceptedGracePeriodMinutes;
    }

    public int getMaxJobsPerScan() { return maxJobsPerScan; }
    public void setMaxJobsPerScan(int maxJobsPerScan) { this.maxJobsPerScan = maxJobsPerScan; }
}
