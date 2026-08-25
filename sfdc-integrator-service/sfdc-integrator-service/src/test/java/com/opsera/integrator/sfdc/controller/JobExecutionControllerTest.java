package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Characterization tests for {@link JobExecutionController} legacy quick deploy routes.
 *
 * <p>These tests capture current contract behavior (HTTP method, path, status code,
 * response body, and service delegation) before v2 coexistence changes are introduced.
 * They must remain green; a failure indicates an accidental change to legacy behavior.
 *
 * <p>See: {@code src/test/resources/fixtures/contracts/legacy-release/legacy-contract-inventory.json}
 * for the machine-readable contract record (LEGACY-001, LEGACY-002).
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
class JobExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuickDeployService quickDeployService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    @MockBean
    private AuditEventWriter auditEventWriter;

    @MockBean
    private ShellArgumentValidator shellArgumentValidator;

    @MockBean
    private com.opsera.integrator.sfdc.security.CallerContextResolver callerContextResolver;

    @MockBean
    private com.opsera.integrator.sfdc.security.ScopeAuthorizer scopeAuthorizer;

    @MockBean
    private TraceContextPropagation traceContextPropagation;

    @MockBean
    private ReleaseLifecycleMetrics releaseLifecycleMetrics;

    @BeforeEach
    void stubTracing() {
        lenient().when(traceContextPropagation.startSpan(anyString()))
                 .thenReturn(TraceContextPropagation.SpanInScope.NOOP);
    }

    // ---- LEGACY-001: POST /quickdeploy — start ----

    @Test
    void startQuickDeploy_validRequest_returns200Success() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-abc123", "pipeline-001", "step-qdeploy-01", "task-fallback-xyz");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }

    @Test
    void startQuickDeploy_validRequest_delegatesToQuickDeployService() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-abc123", "pipeline-001", "step-qdeploy-01", "task-fallback-xyz");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }

    @Test
    void startQuickDeploy_nullFallbackTaskId_normalizesToEmptyAndSucceeds() throws Exception {
        // Fixture: quick-deploy-valid-request-no-fallback.json
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-abc456", "pipeline-002", "step-qdeploy-02", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }

    // ---- AC-5: missing deploymentRequestId is rejected before service delegation ----

    @Test
    void startQuickDeploy_missingDeploymentRequestId_returns400() throws Exception {
        // Fixture: quick-deploy-missing-id.json
        QuickDeployRequest request = new QuickDeployRequest(
                null, "pipeline-004", "step-qdeploy-03", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"));

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void startQuickDeploy_blankDeploymentRequestId_returns400() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "   ", "pipeline-004", "step-qdeploy-03", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"));

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void startQuickDeploy_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json }"))
                .andExpect(status().isBadRequest());

        verify(quickDeployService, never()).start(any());
    }

    // ---- LEGACY-002: POST /quickdeploy/stop — cancellation ----

    @Test
    void stopQuickDeploy_validRequest_returns200Success() throws Exception {
        // Fixture: quick-deploy-stop-request.json
        QuickDeployStopRequest request = new QuickDeployStopRequest(
                "deploy-req-abc123", "pipeline-001");

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(quickDeployService).stop(any(QuickDeployStopRequest.class));
    }

    @Test
    void stopQuickDeploy_validRequest_delegatesToCancellationService() throws Exception {
        QuickDeployStopRequest request = new QuickDeployStopRequest(
                "deploy-req-abc123", "pipeline-001");

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(quickDeployService).stop(any(QuickDeployStopRequest.class));
    }

    @Test
    void stopQuickDeploy_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());

        verify(quickDeployService, never()).stop(any());
    }
}
