package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Integration tests for shell argument guard on {@link JobExecutionController} using
 * the REAL {@link ShellArgumentValidator} (not a mock). Verifies that unsafe inputs
 * are rejected before reaching the service layer (AC-1, AC-3, AC-5).
 *
 * <p>{@link ShellArgumentValidator} is imported directly so the allow-list profiles
 * and injection-pattern detection are exercised end-to-end through the MVC stack.
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import({SfdcExceptionHandler.class, ShellArgumentValidator.class})
class JobExecutionControllerShellGuardTest {

    private static final String FIXTURES = "src/test/resources/fixtures/shell-safety/";

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

    // ---- Valid request passes shell guard ----

    @Test
    void startQuickDeploy_validRequest_passesShellGuardAndReturns200() throws Exception {
        String body = readFixture("valid-deployment-id.json");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }

    // ---- Command separator injection ----

    @Test
    void startQuickDeploy_commandSeparatorInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        String body = readFixture("command-separator-injection.json");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void startQuickDeploy_pipeOperatorInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        String body = readFixture("pipe-operator-injection.json");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    // ---- Path traversal injection ----

    @Test
    void startQuickDeploy_pathTraversalInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        String body = readFixture("path-traversal-injection.json");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    // ---- Oversized value ----

    @Test
    void startQuickDeploy_oversizedDeploymentId_returns400ShellUnsafeInput() throws Exception {
        String body = readFixture("oversized-value.json");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    // ---- Inline injection vectors (not in fixture files) ----

    @Test
    void startQuickDeploy_newlineInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req\ninjected", "pipeline-safe", "step-safe", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void startQuickDeploy_nullByteInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req\0injected", "pipeline-safe", "step-safe", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void startQuickDeploy_commandSeparatorInOptionalFallbackTaskId_returns400ShellUnsafeInput() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-safe-001", "pipeline-safe", "step-safe", "fallback;injected");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).start(any());
    }

    // ---- Stop endpoint: command separator in deploymentRequestId ----

    @Test
    void stopQuickDeploy_commandSeparatorInDeploymentId_returns400ShellUnsafeInput() throws Exception {
        QuickDeployStopRequest req = new QuickDeployStopRequest("deploy;injected", "pipeline-safe");

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).stop(any());
    }

    @Test
    void stopQuickDeploy_pathTraversalInPipelineId_returns400ShellUnsafeInput() throws Exception {
        QuickDeployStopRequest req = new QuickDeployStopRequest("deploy-safe", "pipeline../escape");

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"));

        verify(quickDeployService, never()).stop(any());
    }

    @Test
    void stopQuickDeploy_validRequest_passesShellGuardAndReturns200() throws Exception {
        QuickDeployStopRequest req = new QuickDeployStopRequest("deploy-req-safe-001", "pipeline-safe-001");

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(quickDeployService).stop(any(QuickDeployStopRequest.class));
    }

    // ---- Response structure: field-level error includes field name ----

    @Test
    void startQuickDeploy_injectionAttempt_responseContainsFieldName() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy;injected", "pipeline-safe", "step-safe", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"));
    }

    private String readFixture(String filename) throws Exception {
        return Files.readString(Paths.get(FIXTURES + filename));
    }
}
