package com.opsera.integrator.sfdc.governance.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the automated retention purge process.
 *
 * <p>Bound from {@code sfdc.governance.retention.purge.*} in application.yaml.
 * All defaults are safe — purge is disabled until environment owners explicitly enable it.
 *
 * <p>Properties are non-secret operational configuration and may appear in structured
 * logs (mode, batchSize, maxRuntimeSeconds only — not individual record identifiers).
 */
@ConfigurationProperties(prefix = "sfdc.governance.retention.purge")
public class RetentionPurgeProperties {

    /**
     * Master enabled flag. When false the scheduler exits immediately without
     * querying or mutating any records. Must be explicitly set to true to activate
     * the purge process in any environment.
     * Default: false (safe default — never purge unless explicitly enabled).
     */
    private boolean enabled = false;

    /**
     * Purge operating mode: DISABLED | DRY_RUN | ENFORCE.
     * DISABLED: no candidate selection; DRY_RUN: select but do not erase;
     * ENFORCE: select and physically erase eligible records.
     * Default: DISABLED.
     */
    private String mode = "DISABLED";

    /**
     * Spring cron expression for the scheduled purge run.
     * Default: 2 AM UTC daily.
     */
    private String cron = "0 0 2 * * *";

    /**
     * Maximum number of records to process per purge run.
     * Bounds the blast radius of a single scheduled execution.
     * Default: 100.
     */
    private int batchSize = 100;

    /**
     * Maximum wall-clock seconds for a purge run before it is aborted.
     * Prevents long-running purge from impacting API response time.
     * Default: 300 (5 minutes).
     */
    private long maxRuntimeSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public long getMaxRuntimeSeconds() { return maxRuntimeSeconds; }
    public void setMaxRuntimeSeconds(long maxRuntimeSeconds) { this.maxRuntimeSeconds = maxRuntimeSeconds; }

    /**
     * Resolves the configured mode to the {@link PurgeMode} enum.
     * Returns {@link PurgeMode#DISABLED} for null, blank, or unknown values (fail-closed).
     */
    public PurgeMode resolvedMode() {
        if (mode == null || mode.isBlank()) {
            return PurgeMode.DISABLED;
        }
        try {
            return PurgeMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PurgeMode.DISABLED;
        }
    }
}
