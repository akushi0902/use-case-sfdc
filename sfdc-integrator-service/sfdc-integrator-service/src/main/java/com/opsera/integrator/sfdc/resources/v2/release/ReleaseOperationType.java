package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * Canonical operation type for v2 release commands.
 * Unknown values must be rejected during deserialization or validation.
 */
public enum ReleaseOperationType {
    DEPLOY,
    VALIDATE,
    QUICK_DEPLOY,
    CANCEL
}
