package com.opsera.integrator.sfdc.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LifecycleTransitionPolicy} covering allowed transitions,
 * invalid transitions, terminal-state immutability, and null handling (AC-2, AC-3, AC-5).
 */
class LifecycleTransitionPolicyTest {

    private LifecycleTransitionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LifecycleTransitionPolicy();
    }

    // ---- Allowed transitions from ACCEPTED ----

    @ParameterizedTest
    @CsvSource({"DISPATCHING", "RUNNING", "FAILED", "CANCELLED", "REJECTED"})
    void accepted_allowedTargets_doNotThrow(String target) {
        assertDoesNotThrow(() -> policy.validate(
                JobLifecycleState.ACCEPTED, JobLifecycleState.valueOf(target)));
    }

    @Test
    void accepted_toCompleted_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.ACCEPTED, JobLifecycleState.COMPLETED));
    }

    @Test
    void accepted_toTimedOut_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.ACCEPTED, JobLifecycleState.TIMED_OUT));
    }

    // ---- Allowed transitions from DISPATCHING ----

    @ParameterizedTest
    @CsvSource({"RUNNING", "FAILED", "CANCELLED", "TIMED_OUT"})
    void dispatching_allowedTargets_doNotThrow(String target) {
        assertDoesNotThrow(() -> policy.validate(
                JobLifecycleState.DISPATCHING, JobLifecycleState.valueOf(target)));
    }

    @Test
    void dispatching_toCompleted_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.DISPATCHING, JobLifecycleState.COMPLETED));
    }

    @Test
    void dispatching_toAccepted_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.DISPATCHING, JobLifecycleState.ACCEPTED));
    }

    // ---- Allowed transitions from RUNNING ----

    @ParameterizedTest
    @CsvSource({"COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"})
    void running_allowedTargets_doNotThrow(String target) {
        assertDoesNotThrow(() -> policy.validate(
                JobLifecycleState.RUNNING, JobLifecycleState.valueOf(target)));
    }

    @Test
    void running_toAccepted_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.RUNNING, JobLifecycleState.ACCEPTED));
    }

    @Test
    void running_toDispatching_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.RUNNING, JobLifecycleState.DISPATCHING));
    }

    // ---- Terminal states are immutable ----

    @ParameterizedTest
    @CsvSource({"COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT", "REJECTED"})
    void terminalState_toRunning_throwsWithTerminalMessage(String terminalState) {
        LifecycleTransitionException ex = assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(
                        JobLifecycleState.valueOf(terminalState),
                        JobLifecycleState.RUNNING));
        assertTrue(ex.getMessage().contains("terminal"),
                "Exception message must mention 'terminal' for terminal-source transitions");
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT", "REJECTED"})
    void terminalState_toAccepted_throws(String terminalState) {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(
                        JobLifecycleState.valueOf(terminalState),
                        JobLifecycleState.ACCEPTED));
    }

    // ---- Null handling ----

    @Test
    void validate_nullFrom_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(null, JobLifecycleState.RUNNING));
    }

    @Test
    void validate_nullTo_throws() {
        assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.ACCEPTED, null));
    }

    // ---- isAllowed ----

    @Test
    void isAllowed_validTransition_returnsTrue() {
        assertTrue(policy.isAllowed(JobLifecycleState.RUNNING, JobLifecycleState.COMPLETED));
    }

    @Test
    void isAllowed_invalidTransition_returnsFalse() {
        assertFalse(policy.isAllowed(JobLifecycleState.COMPLETED, JobLifecycleState.RUNNING));
    }

    @Test
    void isAllowed_nullFrom_returnsFalse() {
        assertFalse(policy.isAllowed(null, JobLifecycleState.RUNNING));
    }

    @Test
    void isAllowed_nullTo_returnsFalse() {
        assertFalse(policy.isAllowed(JobLifecycleState.RUNNING, null));
    }

    // ---- allowedFrom ----

    @Test
    void allowedFrom_running_returnsFourTargets() {
        Set<JobLifecycleState> targets = policy.allowedFrom(JobLifecycleState.RUNNING);
        assertEquals(4, targets.size());
        assertTrue(targets.contains(JobLifecycleState.COMPLETED));
        assertTrue(targets.contains(JobLifecycleState.FAILED));
        assertTrue(targets.contains(JobLifecycleState.CANCELLED));
        assertTrue(targets.contains(JobLifecycleState.TIMED_OUT));
    }

    @Test
    void allowedFrom_terminal_returnsEmptySet() {
        Set<JobLifecycleState> targets = policy.allowedFrom(JobLifecycleState.COMPLETED);
        assertTrue(targets.isEmpty());
    }

    @Test
    void allowedFrom_null_returnsEmptySet() {
        Set<JobLifecycleState> targets = policy.allowedFrom(null);
        assertTrue(targets.isEmpty());
    }

    // ---- exception fields ----

    @Test
    void transitionException_containsFromAndToState() {
        LifecycleTransitionException ex = assertThrows(LifecycleTransitionException.class,
                () -> policy.validate(JobLifecycleState.FAILED, JobLifecycleState.RUNNING));
        assertEquals("FAILED", ex.getFromState());
        assertEquals("RUNNING", ex.getToState());
    }
}
