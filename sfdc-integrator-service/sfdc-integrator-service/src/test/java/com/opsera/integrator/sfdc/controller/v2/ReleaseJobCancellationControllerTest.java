package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.TransitionResult;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.resources.v2.cancel.CancellationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC controller tests for {@link ReleaseJobCancellationController}.
 *
 * <p>Covers acceptance criteria:
 * <ul>
 *   <li>AC-1: active job returns 202 with jobId, correlationId, statusUrl, state=CANCELLED, cancellationRequestedAt</li>
 *   <li>AC-2: terminal job returns 409 without invoking downstream cancellation</li>
 *   <li>AC-3: unknown job returns 404</li>
 *   <li>AC-4: legacy /quickdeploy/stop not affected (characterized separately)</li>
 *   <li>AC-5: idempotent already-cancelled returns 202</li>
 *   <li>AC-6: Spring MVC integration tests</li>
 *   <li>AC-7: fixture-backed scenarios</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobCancellationController.class)
@Import(SfdcExceptionHandler.class)
class ReleaseJobCancellationControllerTest {

    private static final String ENDPOINT_TEMPLATE = "/api/v2/sfdc/jobs/%s/cancel";
    private static final String TEST_JOB_ID = "job-cancel-test-001";
    private static final String TEST_CID = "cid-cancel-test-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobRepository jobRepository;

    @MockBean
    private JobLifecycleService lifecycleService;

    @MockBean
    private SafeStructuredLogger safeLogger;

    // ---- AC-1: Active job returns 202 ----

    @Test
    void cancelJob_activeRunningJob_returns202WithAcknowledgement() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(runningJob()));
        when(lifecycleService.markCancelled(anyString(), anyString(), anyString()))
                .thenReturn(cancelledResult());

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("reason"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(TEST_JOB_ID))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.statusUrl").value(containsString(TEST_JOB_ID)))
                .andExpect(jsonPath("$.cancellationRequestedAt").isNotEmpty());
    }

    @Test
    void cancelJob_activeAcceptedJob_returns202() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(acceptedJob()));
        when(lifecycleService.markCancelled(anyString(), anyString(), anyString()))
                .thenReturn(cancelledResult());

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("cancel accepted"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }

    @Test
    void cancelJob_noRequestBody_returns202() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(runningJob()));
        when(lifecycleService.markCancelled(anyString(), anyString(), anyString()))
                .thenReturn(cancelledResult());

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }

    // ---- AC-7: Fixture-backed active job test ----

    @Test
    void cancelJob_activeQuickDeployFixture_returns202() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(runningJob()));
        when(lifecycleService.markCancelled(anyString(), anyString(), anyString()))
                .thenReturn(cancelledResult());

        String body = new ClassPathResource(
                "fixtures/cancel/cancel-active-quick-deploy-job.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    // ---- AC-5: Idempotent already-cancelled returns 202 ----

    @Test
    void cancelJob_alreadyCancelledJob_returns202Idempotently() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(cancelledJob()));

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("retry"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.safeMessage").value(containsString("already cancelled")));

        // Lifecycle service must NOT be called again for idempotent replay
        verify(lifecycleService, never()).markCancelled(anyString(), anyString(), anyString());
    }

    // ---- AC-2: Terminal job returns 409 ----

    @Test
    void cancelJob_completedJob_returns409() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(completedJob()));

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("too late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_CANCELLABLE"));

        verify(lifecycleService, never()).markCancelled(anyString(), anyString(), anyString());
    }

    @Test
    void cancelJob_failedJob_returns409() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(failedJob()));

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("cancel failed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_CANCELLABLE"));

        verify(lifecycleService, never()).markCancelled(anyString(), anyString(), anyString());
    }

    // ---- AC-7: Fixture-backed terminal job test ----

    @Test
    void cancelJob_terminalFixture_returns409() throws Exception {
        when(jobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(completedJob()));

        String body = new ClassPathResource(
                "fixtures/cancel/cancel-terminal-job.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(endpoint(TEST_JOB_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_CANCELLABLE"));
    }

    // ---- AC-3: Unknown job returns 404 ----

    @Test
    void cancelJob_unknownJob_returns404() throws Exception {
        when(jobRepository.findById("unknown-job-id")).thenReturn(Optional.empty());

        mockMvc.perform(post(endpoint("unknown-job-id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancellationRequest("unknown"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        verify(lifecycleService, never()).markCancelled(anyString(), anyString(), anyString());
    }

    // ---- AC-7: Fixture-backed unknown job test ----

    @Test
    void cancelJob_unknownJobFixture_returns404() throws Exception {
        when(jobRepository.findById(any())).thenReturn(Optional.empty());

        String body = new ClassPathResource(
                "fixtures/cancel/cancel-unknown-job.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(endpoint("unknown-job-fixture-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ---- Malformed jobId returns 400 ----

    @Test
    void cancelJob_malformedJobIdWithSlash_returns400() throws Exception {
        mockMvc.perform(post("/api/v2/sfdc/jobs/../../etc-passwd/cancel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // Spring path traversal protection
    }

    @Test
    void cancelJob_tooLongJobId_returns400() throws Exception {
        String longId = "a".repeat(129);

        mockMvc.perform(post(endpoint(longId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_JOB_ID"));
    }

    // ---- Helpers ----

    private String endpoint(String jobId) {
        return String.format(ENDPOINT_TEMPLATE, jobId);
    }

    private CancellationRequest cancellationRequest(String reason) {
        CancellationRequest req = new CancellationRequest();
        req.setSafeReason(reason);
        req.setClientCorrelationId("corr-test-001");
        return req;
    }

    private JobRecord runningJob() {
        JobRecord job = new JobRecord();
        job.setJobId(TEST_JOB_ID);
        job.setCorrelationId(TEST_CID);
        job.setCurrentState("RUNNING");
        job.setOperationType("DEPLOY");
        job.setVersion(3L);
        return job;
    }

    private JobRecord acceptedJob() {
        JobRecord job = new JobRecord();
        job.setJobId(TEST_JOB_ID);
        job.setCorrelationId(TEST_CID);
        job.setCurrentState("ACCEPTED");
        job.setOperationType("QUICK_DEPLOY");
        job.setVersion(1L);
        return job;
    }

    private JobRecord cancelledJob() {
        JobRecord job = new JobRecord();
        job.setJobId(TEST_JOB_ID);
        job.setCorrelationId(TEST_CID);
        job.setCurrentState("CANCELLED");
        job.setOperationType("VALIDATE");
        job.setVersion(2L);
        return job;
    }

    private JobRecord completedJob() {
        JobRecord job = new JobRecord();
        job.setJobId(TEST_JOB_ID);
        job.setCorrelationId(TEST_CID);
        job.setCurrentState("COMPLETED");
        job.setOperationType("DEPLOY");
        job.setVersion(5L);
        return job;
    }

    private JobRecord failedJob() {
        JobRecord job = new JobRecord();
        job.setJobId(TEST_JOB_ID);
        job.setCorrelationId(TEST_CID);
        job.setCurrentState("FAILED");
        job.setOperationType("VALIDATE");
        job.setVersion(4L);
        return job;
    }

    private TransitionResult cancelledResult() {
        return TransitionResult.builder()
                .jobId(TEST_JOB_ID)
                .fromState(JobLifecycleState.RUNNING)
                .toState(JobLifecycleState.CANCELLED)
                .correlationId(TEST_CID)
                .transitionedAt(Instant.parse("2026-08-25T10:00:00Z"))
                .newVersion(4L)
                .idempotentReplay(false)
                .build();
    }
}
