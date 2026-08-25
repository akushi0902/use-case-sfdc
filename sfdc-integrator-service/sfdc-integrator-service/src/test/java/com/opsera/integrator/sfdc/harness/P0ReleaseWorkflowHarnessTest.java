package com.opsera.integrator.sfdc.harness;

import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleService;
import com.opsera.integrator.sfdc.lifecycle.JobLifecycleState;
import com.opsera.integrator.sfdc.lifecycle.JobRepository;
import com.opsera.integrator.sfdc.lifecycle.TransitionResult;
import com.opsera.integrator.sfdc.lifecycle.model.JobRecord;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.deploy.DeploymentSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.FailedStage;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseJobStatusResponse;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackDecision;
import com.opsera.integrator.sfdc.resources.v2.release.RollbackReasonCode;
import com.opsera.integrator.sfdc.resources.v2.validate.ValidationSubmissionAdapter;
import com.opsera.integrator.sfdc.controller.v2.ReleaseJobCancellationController;
import com.opsera.integrator.sfdc.controller.v2.ReleaseJobController;
import com.opsera.integrator.sfdc.controller.v2.ReleaseJobStatusController;
import com.opsera.integrator.sfdc.services.v2.JobStatusAdapter;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 Release Workflow Test Harness — consolidated automated test suite covering
 * the complete P0 release workflow matrix for platform and SRE CI release gating.
 *
 * <p>Covers:
 * <ul>
 *   <li>V2 quick deploy acceptance (POST /api/v2/sfdc/release-jobs/quick-deploy)</li>
 *   <li>V2 deployment acceptance (POST /api/v2/sfdc/release-jobs/deploy)</li>
 *   <li>V2 validation acceptance (POST /api/v2/sfdc/release-jobs/validate)</li>
 *   <li>Release job cancellation — active, terminal, and unknown jobs</li>
 *   <li>Job status lookup — found with rollback fields, not found</li>
 *   <li>Invalid request boundary: facade must NOT be called after validation failure</li>
 *   <li>V2 routes disabled: 422 with structured error, no dispatch</li>
 *   <li>Correlation identifier propagation through accepted responses</li>
 *   <li>Prevalidation warnings surfaced safely in deployment acceptance response</li>
 * </ul>
 *
 * <p>No live Salesforce, Kafka, PostgreSQL, Git, or Kubernetes is required.
 * All external collaborators are mocked. Fixtures loaded from {@code fixtures/p0-harness/}.
 *
 * <p>To run locally:
 * <pre>
 *   ./gradlew test --tests 'com.opsera.integrator.sfdc.harness.P0ReleaseWorkflowHarnessTest'
 * </pre>
 * See also: {@link P0LegacyCompatHarnessTest} for legacy route coverage.
 */
@WebMvcTest(controllers = {
        ReleaseJobController.class,
        ReleaseJobCancellationController.class,
        ReleaseJobStatusController.class
})
@Import(SfdcExceptionHandler.class)
@WithMockUser
@DisplayName("P0 Release Workflow Test Harness — v2 API contract matrix")
class P0ReleaseWorkflowHarnessTest {

    private static final String QUICK_DEPLOY_URL = "/api/v2/sfdc/release-jobs/quick-deploy";
    private static final String DEPLOY_URL       = "/api/v2/sfdc/release-jobs/deploy";
    private static final String VALIDATE_URL     = "/api/v2/sfdc/release-jobs/validate";
    private static final String STATUS_URL       = "/api/v2/sfdc/release-jobs/{jobId}/status";
    private static final String CANCEL_URL       = "/api/v2/sfdc/jobs/{jobId}/cancel";

    private static final String P0_JOB_ID         = "p0-job-harness-001";
    private static final String P0_CORRELATION_ID  = "p0-corr-harness-001";

    @Autowired
    private MockMvc mockMvc;

    // ── Mocks for ReleaseJobController ─────────────────────────────────────────

    @MockBean
    private ReleaseCommandFacade releaseCommandFacade;

    @MockBean
    private V2ReleaseRoutesProperties routesProperties;

    @MockBean
    private QuickDeploySubmissionAdapter quickDeployAdapter;

    @MockBean
    private DeploymentSubmissionAdapter deploymentAdapter;

    @MockBean
    private ValidationSubmissionAdapter validationAdapter;

    // ── Mocks for ReleaseJobCancellationController ─────────────────────────────

    @MockBean
    private JobRepository jobRepository;

    @MockBean
    private JobLifecycleService lifecycleService;

    // ── Mocks for ReleaseJobStatusController ──────────────────────────────────

    @MockBean
    private JobStatusAdapter jobStatusAdapter;

    // ── Shared mocks ──────────────────────────────────────────────────────────

    @MockBean
    private SafeStructuredLogger safeLogger;

    @MockBean
    private ReleaseCoexistenceTelemetry telemetry;

    @BeforeEach
    void enableAllRoutes() {
        lenient().when(routesProperties.isEnabled()).thenReturn(true);
        lenient().when(routesProperties.isOperationEnabled(anyString())).thenReturn(true);
        lenient().when(deploymentAdapter.collectPrevalidationWarnings(any())).thenReturn(List.of());
        lenient().when(validationAdapter.collectPrevalidationWarnings(any())).thenReturn(List.of());
        lenient().when(quickDeployAdapter.toReleaseCommandRequest(any()))
                 .thenReturn(new com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest());
        lenient().when(deploymentAdapter.toReleaseCommandRequest(any()))
                 .thenReturn(new com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest());
        lenient().when(validationAdapter.toReleaseCommandRequest(any()))
                 .thenReturn(new com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest());
        lenient().when(releaseCommandFacade.accept(any()))
                 .thenReturn(P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.QUICK_DEPLOY));
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: V2 Quick Deploy Acceptance
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("V2 Quick Deploy Acceptance (POST /quick-deploy)")
    class QuickDeployAcceptance {

        @Test
        @DisplayName("valid request → 202 with jobId, correlationId, statusUrl, state=ACCEPTED, Location header")
        void validQuickDeploy_returns202WithRequiredFields() throws Exception {
            when(releaseCommandFacade.accept(any()))
                    .thenReturn(P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.QUICK_DEPLOY));

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-quick-deploy-request.json");
            ResultActions result = mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));

            P0WorkflowTestSupport.assertAccepted(result);
            result.andExpect(header().exists("Location"));
        }

        @Test
        @DisplayName("missing deployRequestId → 400 VALIDATION_FAILED, facade NOT called")
        void missingDeployRequestId_returns400_noDispatch() throws Exception {
            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-invalid-quick-deploy-missing-id.json");
            ResultActions result = mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));

            result.andExpect(status().isBadRequest());
            P0WorkflowTestSupport.assertStructuredError(result, "VALIDATION_FAILED");
            verify(releaseCommandFacade, never()).accept(any());
        }

        @Test
        @DisplayName("malformed JSON body → 400, facade NOT called")
        void malformedJson_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not-valid-json"))
                    .andExpect(status().isBadRequest());

            verify(releaseCommandFacade, never()).accept(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: V2 Deployment Acceptance
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("V2 Deployment Acceptance (POST /deploy)")
    class DeploymentAcceptance {

        @Test
        @DisplayName("valid deployment request → 202 with required accepted fields")
        void validDeploy_returns202() throws Exception {
            when(releaseCommandFacade.accept(any()))
                    .thenReturn(P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.DEPLOY));

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-deploy-request.json");
            ResultActions result = mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));

            P0WorkflowTestSupport.assertAccepted(result);
        }

        @Test
        @DisplayName("prevalidation warnings → 202 with safeWarnings in response body")
        void deployWithPrevalidationWarning_returns202WithWarnings() throws Exception {
            when(deploymentAdapter.collectPrevalidationWarnings(any()))
                    .thenReturn(List.of("Unsupported metadata type: CustomLabel"));
            AcceptedAcknowledgement ack = P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.DEPLOY);
            ack.setSafeWarnings(List.of("Unsupported metadata type: CustomLabel"));
            when(releaseCommandFacade.accept(any())).thenReturn(ack);

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-deploy-request.json");
            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.safeWarnings[0]").value("Unsupported metadata type: CustomLabel"));
        }

        @Test
        @DisplayName("missing required customerId → 400 VALIDATION_FAILED, facade NOT called")
        void missingCustomerId_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sfdcToolId\":\"t\",\"taskId\":\"t\"}"))
                    .andExpect(status().isBadRequest());

            verify(releaseCommandFacade, never()).accept(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: V2 Validation Acceptance
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("V2 Validation Acceptance (POST /validate)")
    class ValidationAcceptance {

        @Test
        @DisplayName("valid validation request → 202 with required accepted fields")
        void validValidate_returns202() throws Exception {
            when(releaseCommandFacade.accept(any()))
                    .thenReturn(P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.VALIDATE));

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-validate-request.json");
            ResultActions result = mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));

            P0WorkflowTestSupport.assertAccepted(result);
        }

        @Test
        @DisplayName("missing targetOrgId → 400 VALIDATION_FAILED, facade NOT called")
        void missingTargetOrgId_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"c\",\"sfdcToolId\":\"t\",\"taskId\":\"t\"}"))
                    .andExpect(status().isBadRequest());

            verify(releaseCommandFacade, never()).accept(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: V2 Job Status Lookup
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("V2 Job Status Lookup (GET /release-jobs/{jobId}/status)")
    class StatusLookup {

        @Test
        @DisplayName("found job → 200 with rollback decision fields present")
        void foundJob_returns200WithRollbackFields() throws Exception {
            ReleaseJobStatusResponse response = buildStatusResponseWithRollback(P0_JOB_ID);
            when(jobStatusAdapter.findByJobId(P0_JOB_ID)).thenReturn(Optional.of(response));

            mockMvc.perform(get(STATUS_URL, P0_JOB_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value(P0_JOB_ID))
                    .andExpect(jsonPath("$.state").value("FAILED"))
                    .andExpect(jsonPath("$.rollbackDecision").exists())
                    .andExpect(jsonPath("$.rollbackDecision.rollbackEligible").value(true))
                    .andExpect(jsonPath("$.rollbackDecision.rollbackReasonCode").value("DEPLOYMENT_PARTIAL_FAILURE"))
                    .andExpect(jsonPath("$.rollbackDecision.failedStage").value("DEPLOYMENT"));
        }

        @Test
        @DisplayName("unknown job → 404 JOB_NOT_FOUND")
        void unknownJob_returns404() throws Exception {
            when(jobStatusAdapter.findByJobId("unknown-job-id-p0")).thenReturn(Optional.empty());

            mockMvc.perform(get(STATUS_URL, "unknown-job-id-p0"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
        }

        @Test
        @DisplayName("malformed jobId (SQL injection) → 400 MALFORMED_JOB_ID")
        void malformedJobId_returns400() throws Exception {
            mockMvc.perform(get(STATUS_URL, "job'OR'1'='1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_JOB_ID"));
        }

        private ReleaseJobStatusResponse buildStatusResponseWithRollback(String jobId) {
            ReleaseJobStatusResponse r = new ReleaseJobStatusResponse();
            r.setJobId(jobId);
            r.setCorrelationId(P0_CORRELATION_ID);
            r.setOperationType("DEPLOY");
            r.setState(ReleaseLifecycleState.FAILED);
            r.setAcceptedAt(Instant.parse("2024-01-01T10:00:00Z"));
            r.setUpdatedAt(Instant.parse("2024-01-01T10:05:00Z"));
            r.setCheckpoints(List.of());
            r.setSafeWarnings(List.of());
            r.setStatusSource("DURABLE_LIFECYCLE");
            RollbackDecision decision = RollbackDecision.builder()
                    .rollbackEligible(true)
                    .rollbackRecommended(true)
                    .rollbackReasonCode(RollbackReasonCode.DEPLOYMENT_PARTIAL_FAILURE)
                    .failedStage(FailedStage.DEPLOYMENT)
                    .lastSuccessfulCheckpoint("DEPLOY_APPLY_ERROR")
                    .diagnosticSummary("Deployment component error — partial changes applied")
                    .updatedAt(Instant.parse("2024-01-01T10:05:00Z"))
                    .build();
            r.setRollbackDecision(decision);
            return r;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: Release Job Cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Release Job Cancellation (POST /jobs/{jobId}/cancel)")
    class CancellationWorkflow {

        @Test
        @DisplayName("active job cancellation → 202 with CANCELLED state")
        void activeJob_returns202() throws Exception {
            JobRecord activeJob = buildJobRecord("p0-cancel-active-001", "RUNNING");
            when(jobRepository.findById("p0-cancel-active-001")).thenReturn(Optional.of(activeJob));
            TransitionResult result = TransitionResult.builder()
                    .jobId("p0-cancel-active-001")
                    .fromState(JobLifecycleState.RUNNING)
                    .toState(JobLifecycleState.CANCELLED)
                    .correlationId("p0-corr-cancel-active-001")
                    .newVersion(1L)
                    .build();
            when(lifecycleService.markCancelled(anyString(), anyString(), anyString())).thenReturn(result);

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-cancel-request.json");
            mockMvc.perform(post(CANCEL_URL, "p0-cancel-active-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobId").value("p0-cancel-active-001"))
                    .andExpect(jsonPath("$.state").value("CANCELLED"));
        }

        @Test
        @DisplayName("terminal COMPLETED job → 409 JOB_NOT_CANCELLABLE, no downstream dispatch")
        void terminalJob_returns409_noDispatch() throws Exception {
            JobRecord terminalJob = buildJobRecord("p0-cancel-terminal-001", "COMPLETED");
            when(jobRepository.findById("p0-cancel-terminal-001")).thenReturn(Optional.of(terminalJob));

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-cancel-request.json");
            mockMvc.perform(post(CANCEL_URL, "p0-cancel-terminal-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_CANCELLABLE"));

            verify(lifecycleService, never()).markCancelled(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("unknown job → 404 JOB_NOT_FOUND")
        void unknownJob_returns404() throws Exception {
            when(jobRepository.findById("p0-cancel-unknown-001")).thenReturn(Optional.empty());

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-cancel-request.json");
            mockMvc.perform(post(CANCEL_URL, "p0-cancel-unknown-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isNotFound());
        }

        private JobRecord buildJobRecord(String jobId, String state) {
            JobRecord job = new JobRecord();
            job.setJobId(jobId);
            job.setCorrelationId("p0-corr-cancel-" + jobId);
            job.setOperationType("DEPLOY");
            job.setCurrentState(state);
            job.setVersion(0);
            job.setCreatedAt(Instant.parse("2024-01-01T10:00:00Z"));
            job.setUpdatedAt(Instant.parse("2024-01-01T10:05:00Z"));
            return job;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: V2 Routes Disabled — no downstream dispatch
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("V2 Routes Disabled — all operations return 422, facade NOT called")
    class RoutesDisabled {

        @BeforeEach
        void disableRoutes() {
            when(routesProperties.isEnabled()).thenReturn(false);
        }

        @Test
        @DisplayName("quick deploy with routes disabled → 422 V2_ROUTES_DISABLED, facade NOT called")
        void quickDeploy_routesDisabled_returns422() throws Exception {
            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-quick-deploy-request.json");
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("V2_ROUTES_DISABLED"));

            verify(releaseCommandFacade, never()).accept(any());
        }

        @Test
        @DisplayName("deploy with routes disabled → 422 V2_ROUTES_DISABLED, facade NOT called")
        void deploy_routesDisabled_returns422() throws Exception {
            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-deploy-request.json");
            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("V2_ROUTES_DISABLED"));

            verify(releaseCommandFacade, never()).accept(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Correlation identifier propagation
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Correlation ID propagation through accepted responses")
    class CorrelationPropagation {

        @Test
        @DisplayName("accepted response carries a non-empty correlationId matching the ack")
        void acceptedResponse_hasCorrelationId() throws Exception {
            when(releaseCommandFacade.accept(any()))
                    .thenReturn(P0WorkflowTestSupport.buildAck(P0_JOB_ID, P0_CORRELATION_ID, ReleaseOperationType.QUICK_DEPLOY));

            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-quick-deploy-request.json");
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.correlationId").value(P0_CORRELATION_ID))
                    .andExpect(jsonPath("$.jobId").value(P0_JOB_ID))
                    .andExpect(jsonPath("$.statusUrl").value(
                            "/api/v2/sfdc/release-jobs/" + P0_JOB_ID + "/status"));
        }
    }
}
