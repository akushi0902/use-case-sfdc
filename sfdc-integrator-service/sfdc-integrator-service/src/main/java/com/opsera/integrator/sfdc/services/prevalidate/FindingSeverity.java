package com.opsera.integrator.sfdc.services.prevalidate;

/**
 * Severity classification for a prevalidation finding.
 *
 * <p>{@link #HARD_FAILURE} findings block release submission; the caller must reject the request.
 * {@link #WARNING} findings are advisory and may be forwarded to the submitter without blocking.
 */
public enum FindingSeverity {

    /** Blocks release submission. The caller must return a validation error to the submitter. */
    HARD_FAILURE,

    /** Advisory only. Release submission may proceed but the submitter should be informed. */
    WARNING
}
