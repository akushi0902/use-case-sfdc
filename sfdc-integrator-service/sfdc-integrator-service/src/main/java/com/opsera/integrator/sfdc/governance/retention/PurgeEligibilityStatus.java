package com.opsera.integrator.sfdc.governance.retention;

/**
 * Computed purge eligibility status for a retention metadata record.
 *
 * <p>LEGAL_HOLD always overrides ELIGIBLE — records under legal hold
 * must not be included in automated purge selection regardless of
 * the retention expiration timestamp.
 */
public enum PurgeEligibilityStatus {

    /**
     * Retention expiration has passed and no legal hold is active.
     * The record is a candidate for automated purge.
     */
    ELIGIBLE,

    /**
     * Retention expiration has not yet passed.
     * The record must not be purged.
     */
    INELIGIBLE,

    /**
     * A legal hold is active on this record.
     * Automated purge is blocked regardless of retention expiration.
     */
    LEGAL_HOLD,

    /**
     * Eligibility cannot be determined (e.g. retention policy was not resolved).
     * Treated as INELIGIBLE by purge automation (fail-closed).
     */
    UNKNOWN
}
