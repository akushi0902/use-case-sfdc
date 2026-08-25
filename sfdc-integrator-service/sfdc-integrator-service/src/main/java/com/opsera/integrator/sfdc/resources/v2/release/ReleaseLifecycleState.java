package com.opsera.integrator.sfdc.resources.v2.release;

/**
 * Canonical lifecycle state for v2 release jobs.
 *
 * <p>DISPATCHING and TIMED_OUT were added when the v2 status adapter was introduced
 * to preserve full fidelity from the durable lifecycle storage without coercing
 * intermediate states into coarser equivalents.
 */
public enum ReleaseLifecycleState {
    /** Command accepted and persisted; worker dispatch pending. */
    ACCEPTED,
    /** Worker dispatch in progress; handoff to execution layer pending. */
    DISPATCHING,
    /** Worker actively executing the release operation. */
    RUNNING,
    /** Release operation completed successfully. Terminal. */
    COMPLETED,
    /** Release operation failed. Terminal. */
    FAILED,
    /** Job cancelled before or during execution. Terminal. */
    CANCELLED,
    /** Job exceeded its execution time limit without completing. Terminal. */
    TIMED_OUT
}
