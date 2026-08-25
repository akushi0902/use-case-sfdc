package com.opsera.integrator.sfdc.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobLifecycleState} — isTerminal, isActive, fromString,
 * and canonical state set coverage (AC-1, AC-5).
 */
class JobLifecycleStateTest {

    // ---- isTerminal ----

    @Test
    void completed_isTerminal() {
        assertTrue(JobLifecycleState.COMPLETED.isTerminal());
    }

    @Test
    void failed_isTerminal() {
        assertTrue(JobLifecycleState.FAILED.isTerminal());
    }

    @Test
    void cancelled_isTerminal() {
        assertTrue(JobLifecycleState.CANCELLED.isTerminal());
    }

    @Test
    void timedOut_isTerminal() {
        assertTrue(JobLifecycleState.TIMED_OUT.isTerminal());
    }

    @Test
    void rejected_isTerminal() {
        assertTrue(JobLifecycleState.REJECTED.isTerminal());
    }

    @Test
    void accepted_notTerminal() {
        assertFalse(JobLifecycleState.ACCEPTED.isTerminal());
    }

    @Test
    void dispatching_notTerminal() {
        assertFalse(JobLifecycleState.DISPATCHING.isTerminal());
    }

    @Test
    void running_notTerminal() {
        assertFalse(JobLifecycleState.RUNNING.isTerminal());
    }

    // ---- isActive ----

    @Test
    void accepted_isActive() {
        assertTrue(JobLifecycleState.ACCEPTED.isActive());
    }

    @Test
    void dispatching_isActive() {
        assertTrue(JobLifecycleState.DISPATCHING.isActive());
    }

    @Test
    void running_isActive() {
        assertTrue(JobLifecycleState.RUNNING.isActive());
    }

    @Test
    void completed_notActive() {
        assertFalse(JobLifecycleState.COMPLETED.isActive());
    }

    @Test
    void failed_notActive() {
        assertFalse(JobLifecycleState.FAILED.isActive());
    }

    // ---- fromString ----

    @Test
    void fromString_validUpperCase_parsed() {
        assertEquals(JobLifecycleState.ACCEPTED, JobLifecycleState.fromString("ACCEPTED"));
        assertEquals(JobLifecycleState.RUNNING, JobLifecycleState.fromString("RUNNING"));
        assertEquals(JobLifecycleState.TIMED_OUT, JobLifecycleState.fromString("TIMED_OUT"));
    }

    @Test
    void fromString_validLowerCase_parsed() {
        assertEquals(JobLifecycleState.COMPLETED, JobLifecycleState.fromString("completed"));
        assertEquals(JobLifecycleState.FAILED, JobLifecycleState.fromString("failed"));
    }

    @Test
    void fromString_unknown_throwsTransitionException() {
        assertThrows(LifecycleTransitionException.class,
                () -> JobLifecycleState.fromString("UNKNOWN_STATE"));
    }

    @Test
    void fromString_null_throwsTransitionException() {
        assertThrows(LifecycleTransitionException.class,
                () -> JobLifecycleState.fromString(null));
    }

    @Test
    void fromString_blank_throwsTransitionException() {
        assertThrows(LifecycleTransitionException.class,
                () -> JobLifecycleState.fromString("  "));
    }

    // ---- Canonical state count ----

    @Test
    void allEightStatesAreDefined() {
        assertEquals(8, JobLifecycleState.values().length,
                "State machine must define exactly 8 canonical states");
    }

    @Test
    void fiveTerminalStates_threeActiveStates() {
        long terminalCount = 0;
        long activeCount = 0;
        for (JobLifecycleState s : JobLifecycleState.values()) {
            if (s.isTerminal()) terminalCount++;
            if (s.isActive()) activeCount++;
        }
        assertEquals(5, terminalCount, "Expected 5 terminal states");
        assertEquals(3, activeCount, "Expected 3 active states");
    }
}
