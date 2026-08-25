package com.opsera.integrator.sfdc.lifecycle;

import com.opsera.integrator.sfdc.lifecycle.model.JobCheckpoint;
import com.opsera.integrator.sfdc.lifecycle.model.JobDiagnostic;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobLifecycleService} using a mocked {@link JobRepository}.
 * No Spring context required (AC-5, AC-2 — independent of MVC/Kafka).
 */
class JobLifecycleServiceTest {

    private JobRepository repository;
    private JobLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = mock(JobRepository.class);
        service = new JobLifecycleService(repository);
    }

    // ---- Successful transitions ----

    @Test
    void transition_acceptedToRunning_updatesStateAndReturnsResult() {
        when(repository.findById("job-001")).thenReturn(Optional.of(jobRecord("job-001", "ACCEPTED", 0L)));

        TransitionRequest req = TransitionRequest.builder("job-001", JobLifecycleState.RUNNING)
                .correlationId("cid-001").build();
        TransitionResult result = service.transition(req);

        verify(repository).updateState("job-001", "RUNNING", 0L);
        assertEquals(JobLifecycleState.ACCEPTED, result.getFromState());
        assertEquals(JobLifecycleState.RUNNING, result.getToState());
        assertEquals(1L, result.getNewVersion());
        assertFalse(result.isIdempotentReplay());
    }

    @Test
    void transition_runningToCompleted_persistsNoCheckpointWhenSeqNegative() {
        when(repository.findById("job-002")).thenReturn(Optional.of(jobRecord("job-002", "RUNNING", 1L)));

        service.transition(TransitionRequest.builder("job-002", JobLifecycleState.COMPLETED)
                .correlationId("cid-002").build());

        verify(repository, never()).saveCheckpoint(any());
    }

    @Test
    void transition_runningToCompleted_persistsCheckpointWhenSeqProvided() {
        when(repository.findById("job-003")).thenReturn(Optional.of(jobRecord("job-003", "RUNNING", 1L)));

        service.transition(TransitionRequest.builder("job-003", JobLifecycleState.COMPLETED)
                .correlationId("cid-003")
                .checkpointSequenceNumber(2)
                .checkpointCode("EXECUTION_COMPLETED")
                .build());

        ArgumentCaptor<JobCheckpoint> cpCaptor = ArgumentCaptor.forClass(JobCheckpoint.class);
        verify(repository).saveCheckpoint(cpCaptor.capture());
        assertEquals("job-003", cpCaptor.getValue().getJobId());
        assertEquals(2, cpCaptor.getValue().getSequenceNumber());
        assertEquals("COMPLETED", cpCaptor.getValue().getStateAtCheckpoint());
    }

    // ---- Terminal failure states persist diagnostics ----

    @Test
    void transition_runningToFailed_persistsDiagnostic() {
        when(repository.findById("job-004")).thenReturn(Optional.of(jobRecord("job-004", "RUNNING", 2L)));

        service.transition(TransitionRequest.builder("job-004", JobLifecycleState.FAILED)
                .correlationId("cid-004")
                .safeReasonCode("SFDC_CONNECT_FAILED")
                .safeDiagnosticSummary("Org connection refused after 3 attempts")
                .build());

        ArgumentCaptor<JobDiagnostic> diagCaptor = ArgumentCaptor.forClass(JobDiagnostic.class);
        verify(repository).saveDiagnostic(diagCaptor.capture());
        assertEquals("job-004", diagCaptor.getValue().getJobId());
        assertEquals("SFDC_CONNECT_FAILED", diagCaptor.getValue().getErrorCode());
        assertEquals("Org connection refused after 3 attempts", diagCaptor.getValue().getSafeSummary());
    }

    @Test
    void transition_toFailed_withNullReasonCode_usesDefaultCode() {
        when(repository.findById("job-005")).thenReturn(Optional.of(jobRecord("job-005", "RUNNING", 1L)));

        service.transition(TransitionRequest.builder("job-005", JobLifecycleState.FAILED)
                .correlationId("cid-005").build());

        ArgumentCaptor<JobDiagnostic> diagCaptor = ArgumentCaptor.forClass(JobDiagnostic.class);
        verify(repository).saveDiagnostic(diagCaptor.capture());
        assertEquals(JobLifecycleService.DEFAULT_FAILURE_CODE, diagCaptor.getValue().getErrorCode());
    }

    @Test
    void transition_toTimedOut_persistsDiagnostic() {
        when(repository.findById("job-006")).thenReturn(Optional.of(jobRecord("job-006", "RUNNING", 1L)));

        service.transition(TransitionRequest.builder("job-006", JobLifecycleState.TIMED_OUT)
                .correlationId("cid-006").safeReasonCode("EXECUTION_TIMEOUT").build());

        verify(repository).saveDiagnostic(any());
    }

    @Test
    void transition_toRejected_persistsDiagnostic() {
        when(repository.findById("job-007")).thenReturn(Optional.of(jobRecord("job-007", "ACCEPTED", 0L)));

        service.transition(TransitionRequest.builder("job-007", JobLifecycleState.REJECTED)
                .correlationId("cid-007").safeReasonCode("POLICY_VIOLATION").build());

        verify(repository).saveDiagnostic(any());
    }

    @Test
    void transition_toCompleted_doesNotPersistDiagnostic() {
        when(repository.findById("job-008")).thenReturn(Optional.of(jobRecord("job-008", "RUNNING", 1L)));

        service.transition(TransitionRequest.builder("job-008", JobLifecycleState.COMPLETED)
                .correlationId("cid-008").build());

        verify(repository, never()).saveDiagnostic(any());
    }

    // ---- Idempotent replay ----

    @Test
    void transition_alreadyInTargetState_returnsIdempotentReplay() {
        when(repository.findById("job-010")).thenReturn(Optional.of(jobRecord("job-010", "RUNNING", 2L)));

        TransitionResult result = service.transition(
                TransitionRequest.builder("job-010", JobLifecycleState.RUNNING).build());

        assertTrue(result.isIdempotentReplay());
        verify(repository, never()).updateState(any(), any(), anyLong());
        verify(repository, never()).saveCheckpoint(any());
        verify(repository, never()).saveDiagnostic(any());
    }

    @Test
    void transition_idempotentReplay_returnsExistingVersion() {
        when(repository.findById("job-011")).thenReturn(Optional.of(jobRecord("job-011", "COMPLETED", 5L)));

        TransitionResult result = service.transition(
                TransitionRequest.builder("job-011", JobLifecycleState.COMPLETED).build());

        assertTrue(result.isIdempotentReplay());
        assertEquals(5L, result.getNewVersion());
    }

    // ---- Invalid transitions ----

    @Test
    void transition_completedToRunning_throwsTransitionException() {
        when(repository.findById("job-020")).thenReturn(Optional.of(jobRecord("job-020", "COMPLETED", 3L)));

        assertThrows(LifecycleTransitionException.class, () ->
                service.transition(TransitionRequest.builder("job-020", JobLifecycleState.RUNNING).build()));

        verify(repository, never()).updateState(any(), any(), anyLong());
    }

    @Test
    void transition_failedToRunning_doesNotMutateState() {
        when(repository.findById("job-021")).thenReturn(Optional.of(jobRecord("job-021", "FAILED", 2L)));

        assertThrows(LifecycleTransitionException.class, () ->
                service.transition(TransitionRequest.builder("job-021", JobLifecycleState.RUNNING).build()));

        verify(repository, never()).updateState(any(), any(), anyLong());
    }

    @Test
    void transition_runningToAccepted_throwsTransitionException() {
        when(repository.findById("job-022")).thenReturn(Optional.of(jobRecord("job-022", "RUNNING", 1L)));

        assertThrows(LifecycleTransitionException.class, () ->
                service.transition(TransitionRequest.builder("job-022", JobLifecycleState.ACCEPTED).build()));
    }

    // ---- Missing job ----

    @Test
    void transition_jobNotFound_throwsPersistenceException() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(LifecyclePersistenceException.class, () ->
                service.transition(TransitionRequest.builder("nonexistent", JobLifecycleState.RUNNING).build()));
    }

    // ---- Null request ----

    @Test
    void transition_nullRequest_throwsTransitionException() {
        assertThrows(LifecycleTransitionException.class, () -> service.transition(null));
    }

    // ---- Optimistic concurrency conflict ----

    @Test
    void transition_concurrencyConflict_throwsConcurrencyException() {
        when(repository.findById("job-030")).thenReturn(Optional.of(jobRecord("job-030", "ACCEPTED", 1L)));
        doThrow(new LifecyclePersistenceException("version mismatch"))
                .when(repository).updateState(eq("job-030"), any(), eq(1L));

        assertThrows(LifecycleConcurrencyException.class, () ->
                service.transition(TransitionRequest.builder("job-030", JobLifecycleState.RUNNING).build()));
    }

    @Test
    void concurrencyException_containsJobIdAndVersion() {
        when(repository.findById("job-031")).thenReturn(Optional.of(jobRecord("job-031", "ACCEPTED", 7L)));
        doThrow(new LifecyclePersistenceException("conflict"))
                .when(repository).updateState(eq("job-031"), any(), eq(7L));

        LifecycleConcurrencyException ex = assertThrows(LifecycleConcurrencyException.class, () ->
                service.transition(TransitionRequest.builder("job-031", JobLifecycleState.RUNNING).build()));
        assertEquals("job-031", ex.getJobId());
        assertEquals(7L, ex.getExpectedVersion());
    }

    // ---- Convenience methods ----

    @Test
    void markFailed_withBlankReasonCode_usesDefaultCode() {
        when(repository.findById("job-040")).thenReturn(Optional.of(jobRecord("job-040", "RUNNING", 1L)));

        service.markFailed("job-040", "cid-040", "  ", "Safe summary");

        ArgumentCaptor<JobDiagnostic> captor = ArgumentCaptor.forClass(JobDiagnostic.class);
        verify(repository).saveDiagnostic(captor.capture());
        assertEquals(JobLifecycleService.DEFAULT_FAILURE_CODE, captor.getValue().getErrorCode());
    }

    @Test
    void markCompleted_callsUpdateState() {
        when(repository.findById("job-041")).thenReturn(Optional.of(jobRecord("job-041", "RUNNING", 2L)));

        service.markCompleted("job-041", "cid-041", -1);

        verify(repository).updateState("job-041", "COMPLETED", 2L);
    }

    @Test
    void markCancelled_callsUpdateState() {
        when(repository.findById("job-042")).thenReturn(Optional.of(jobRecord("job-042", "RUNNING", 1L)));

        service.markCancelled("job-042", "cid-042", "USER_REQUESTED");

        verify(repository).updateState("job-042", "CANCELLED", 1L);
    }

    // ---- Diagnostic truncation ----

    @Test
    void transition_longDiagnosticSummary_truncatedTo1024Chars() {
        when(repository.findById("job-050")).thenReturn(Optional.of(jobRecord("job-050", "RUNNING", 1L)));
        String longSummary = "X".repeat(1500);

        service.transition(TransitionRequest.builder("job-050", JobLifecycleState.FAILED)
                .safeDiagnosticSummary(longSummary).build());

        ArgumentCaptor<JobDiagnostic> captor = ArgumentCaptor.forClass(JobDiagnostic.class);
        verify(repository).saveDiagnostic(captor.capture());
        assertTrue(captor.getValue().getSafeSummary().length() <= 1024,
                "Diagnostic summary must be truncated to 1024 characters");
    }

    // ---- Helper ----

    private JobRecord jobRecord(String jobId, String state, long version) {
        JobRecord r = new JobRecord();
        r.setJobId(jobId);
        r.setCorrelationId("cid-" + jobId);
        r.setCurrentState(state);
        r.setVersion(version);
        r.setUpdatedAt(Instant.now());
        return r;
    }
}
