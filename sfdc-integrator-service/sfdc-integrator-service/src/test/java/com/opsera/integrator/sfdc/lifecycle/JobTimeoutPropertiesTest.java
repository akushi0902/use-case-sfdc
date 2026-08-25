package com.opsera.integrator.sfdc.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobTimeoutProperties} default values and setter binding.
 * No Spring context required — verifies safe defaults and property wiring (AC-4, AC-5).
 */
class JobTimeoutPropertiesTest {

    @Test
    void defaults_enabledTrue() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertTrue(props.isEnabled(), "Monitor must be enabled by default");
    }

    @Test
    void defaults_scanIntervalSeconds_300() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertEquals(300L, props.getScanIntervalSeconds());
    }

    @Test
    void defaults_staleThresholdMinutes_240() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertEquals(240L, props.getStaleThresholdMinutes());
    }

    @Test
    void defaults_acceptedGracePeriodMinutes_30() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertEquals(30L, props.getAcceptedGracePeriodMinutes());
    }

    @Test
    void defaults_maxJobsPerScan_100() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertEquals(100, props.getMaxJobsPerScan());
    }

    @Test
    void setEnabled_false_reflected() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }

    @Test
    void setScanIntervalSeconds_reflected() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        props.setScanIntervalSeconds(60L);
        assertEquals(60L, props.getScanIntervalSeconds());
    }

    @Test
    void setStaleThresholdMinutes_reflected() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        props.setStaleThresholdMinutes(120L);
        assertEquals(120L, props.getStaleThresholdMinutes());
    }

    @Test
    void setAcceptedGracePeriodMinutes_reflected() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        props.setAcceptedGracePeriodMinutes(15L);
        assertEquals(15L, props.getAcceptedGracePeriodMinutes());
    }

    @Test
    void setMaxJobsPerScan_reflected() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        props.setMaxJobsPerScan(50);
        assertEquals(50, props.getMaxJobsPerScan());
    }

    @Test
    void defaults_scanInterval_safeLongRunning() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertTrue(props.getScanIntervalSeconds() >= 60,
                "Scan interval must be at least 60s to avoid excessive DB load");
    }

    @Test
    void defaults_staleThreshold_safeLongRunning() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertTrue(props.getStaleThresholdMinutes() >= 60,
                "Stale threshold must be at least 60 min to protect long-running jobs");
    }

    @Test
    void defaults_acceptedGracePeriod_safeLongRunning() {
        JobTimeoutProperties props = new JobTimeoutProperties();
        assertTrue(props.getAcceptedGracePeriodMinutes() >= 5,
                "Grace period must be at least 5 min to protect fresh accepted jobs");
    }
}
