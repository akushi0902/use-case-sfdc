package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * Canonical lifecycle state for v2 release jobs.
 */
public enum ReleaseLifecycleState {
    ACCEPTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
