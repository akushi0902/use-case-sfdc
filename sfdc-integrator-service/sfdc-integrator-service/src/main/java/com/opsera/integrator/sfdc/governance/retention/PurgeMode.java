package com.opsera.integrator.sfdc.governance.retention;

/**
 * Operating mode for the automated retention purge process.
 *
 * <p>DISABLED is the safe default — no candidate selection or deletion occurs.
 * Environment owners must explicitly configure DRY_RUN or ENFORCE.
 */
public enum PurgeMode {

    /**
     * Purge is fully disabled. The scheduler exits immediately without querying
     * or mutating any records. Use this mode until the purge process has been
     * reviewed and approved for the target environment.
     */
    DISABLED,

    /**
     * Candidates are selected and counted, but no records are mutated or erased.
     * Audit events and structured logs reflect what would have been purged.
     * Use this mode to gain operational confidence before enabling enforce mode.
     */
    DRY_RUN,

    /**
     * Candidates are selected and governed data is physically erased.
     * Sensitive fields in the job record are nulled out (cryptographic erasure semantics).
     * Audit events are emitted for each batch outcome.
     * Only enable this mode after dry-run validation and environment owner approval.
     */
    ENFORCE
}
