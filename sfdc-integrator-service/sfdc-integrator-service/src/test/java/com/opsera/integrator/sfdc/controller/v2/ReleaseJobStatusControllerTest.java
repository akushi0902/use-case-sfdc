package com.opsera.integrator.sfdc.controller.v2;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.services.v2.JobStatusAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ReleaseJobStatusController}.
 *
 * <p>Uses MockMvc + @WebMvcTest so no full ApplicationContext starts.
 * Tests: 200 found, 404 not-found, 400 malformed jobId variants, 503 persistence failure.
 */
@WebMvcTest(ReleaseJobStatusController.class)
@Import(SfdcExceptionHandler.class)
@WithMockUser
@DisplayName("ReleaseJobStatusController — slice tests")
class ReleaseJobStatusControllerTest {

    private static final String STATUS_URL = "/api/v2/sfdc/release-jobs/{jobId}/status";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobStatusAdapter statusAdapter;

    private ReleaseJobStatusResponse running(String jobId) {
        ReleaseJobStatusResponse r = new ReleaseJobStatusResponse();
        r.setJobId(jobId);
        r.setCorrelationId("corr-ctrl-test-001");
        r.setOperationType("QUICK_DEPLOY");
        r.setState(ReleaseLifecycleState.RUNNING);
        r.setAcceptedAt(Instant.parse("2024-01-01T10:00:00Z"));
        r.setUpdatedAt(Instant.parse("2024-01-01T10:01:00Z"));
        r.setCheckpoints(List.of());
        r.setSafeWarnings(List.of());
        r.setStatusSource("DURABLE_LIFECYCLE");
        return r;
    }

    // ── 200 Found ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("200 — job found")
    class JobFound {

        @Test
        @DisplayName("returns 200 and populated body for a known jobId")
        void getJobStatus_knownJob_returns200() throws Exception {
            String jobId = "job-ctrl-test-001";
            when(statusAdapter.findByJobId(jobId)).thenReturn(Optional.of(running(jobId)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value(jobId))
                    .andExpect(jsonPath("$.state").value("RUNNING"))
                    .andExpect(jsonPath("$.statusSource").value("DURABLE_LIFECYCLE"))
                    .andExpect(jsonPath("$.correlationId").value("corr-ctrl-test-001"));
        }

        @Test
        @DisplayName("jobId with hyphens and underscores is accepted (safe pattern match)")
        void getJobStatus_jobIdWithHyphensAndUnderscores_returns200() throws Exception {
            String jobId = "job_ctrl-test-00X";
            when(statusAdapter.findByJobId(jobId)).thenReturn(Optional.of(running(jobId)));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value(jobId));
        }
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("404 — job not found")
    class JobNotFound {

        @Test
        @DisplayName("returns 404 with JOB_NOT_FOUND errorCode for unknown jobId")
        void getJobStatus_unknownJob_returns404() throws Exception {
            when(statusAdapter.findByJobId("job-unknown-001")).thenReturn(Optional.empty());

            mockMvc.perform(get(STATUS_URL, "job-unknown-001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
        }
    }

    // ── 400 Malformed jobId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("400 — malformed jobId")
    class MalformedJobId {

        @Test
        @DisplayName("jobId exceeding 128 characters returns 400 MALFORMED_JOB_ID")
        void getJobStatus_jobIdTooLong_returns400() throws Exception {
            String longId = "a".repeat(129);

            mockMvc.perform(get(STATUS_URL, longId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_JOB_ID"));
        }

        @Test
        @DisplayName("jobId with path-traversal characters returns 400 MALFORMED_JOB_ID")
        void getJobStatus_jobIdWithSlash_returns400() throws Exception {
            String unsafeId = "job/../evil";

            mockMvc.perform(get("/api/v2/sfdc/release-jobs/" + unsafeId + "/status"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("jobId with SQL injection characters returns 400 MALFORMED_JOB_ID")
        void getJobStatus_jobIdWithSqlInjection_returns400() throws Exception {
            String unsafeId = "job'OR'1'='1";

            mockMvc.perform(get(STATUS_URL, unsafeId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_JOB_ID"));
        }

        @Test
        @DisplayName("jobId with spaces returns 400 MALFORMED_JOB_ID")
        void getJobStatus_jobIdWithSpaces_returns400() throws Exception {
            String unsafeId = "job ctrl test";

            mockMvc.perform(get(STATUS_URL, unsafeId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_JOB_ID"));
        }
    }

    // ── 503 Persistence failure ────────────────────────────────────────────────

    @Nested
    @DisplayName("503 — persistence unavailable")
    class PersistenceUnavailable {

        @Test
        @DisplayName("LifecyclePersistenceException from adapter returns 503 STATUS_SOURCE_UNAVAILABLE")
        void getJobStatus_persistenceException_returns503() throws Exception {
            String jobId = "job-503-test-001";
            when(statusAdapter.findByJobId(jobId))
                    .thenThrow(new LifecyclePersistenceException("DB connection failure"));

            mockMvc.perform(get(STATUS_URL, jobId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("STATUS_SOURCE_UNAVAILABLE"));
        }
    }
}
