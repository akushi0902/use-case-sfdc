package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests validating that quick-deploy requests with and without
 * X-Correlation-Id produce the expected correlation response header behavior without
 * changing the legacy HTTP 200 / "SUCCESS" response contract (AC-8, AC-9).
 *
 * <p>Correlation header behavior is provided by {@link com.opsera.integrator.sfdc.correlation.CorrelationIdFilter}
 * (WO-105). These tests verify the controller path works end-to-end with tracing enabled.
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Trace Context (WO-144)")
class JobExecutionControllerTraceContextTest {

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

    // ---- AC-8: Legacy response contract unchanged ----

    @Test
    @DisplayName("POST /quickdeploy with X-Correlation-Id returns HTTP 200 SUCCESS and echoes correlation header")
    void startQuickDeploy_withCorrelationId_returns200AndEchosCorrelationHeader() throws Exception {
        QuickDeployRequest request = validStartRequest();

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-001"));
    }

    @Test
    @DisplayName("POST /quickdeploy without X-Correlation-Id returns HTTP 200 SUCCESS and generates a correlation header")
    void startQuickDeploy_withoutCorrelationId_returns200AndGeneratesCorrelationHeader() throws Exception {
        QuickDeployRequest request = validStartRequest();

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"))
                .andExpect(header().exists(CorrelationIdConstants.HEADER_NAME));
    }

    @Test
    @DisplayName("POST /quickdeploy/stop with X-Correlation-Id returns HTTP 200 SUCCESS and echoes correlation header")
    void stopQuickDeploy_withCorrelationId_returns200AndEchosCorrelationHeader() throws Exception {
        QuickDeployStopRequest request = validStopRequest();

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-002")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-002"));
    }

    @Test
    @DisplayName("POST /quickdeploy/stop without X-Correlation-Id returns HTTP 200 SUCCESS and generates a correlation header")
    void stopQuickDeploy_withoutCorrelationId_returns200AndGeneratesCorrelationHeader() throws Exception {
        QuickDeployStopRequest request = validStopRequest();

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"))
                .andExpect(header().exists(CorrelationIdConstants.HEADER_NAME));
    }

    @Test
    @DisplayName("POST /quickdeploy with unsafe correlation header gets generated replacement, still returns 200")
    void startQuickDeploy_withUnsafeCorrelationId_getsReplacement() throws Exception {
        QuickDeployRequest request = validStartRequest();

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdConstants.HEADER_NAME, "unsafe\nheader\nvalue")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"))
                .andExpect(header().exists(CorrelationIdConstants.HEADER_NAME));
    }

    // ---- AC-3: Validation failure still returns proper error, correlation header present ----

    @Test
    @DisplayName("POST /quickdeploy with missing deploymentRequestId returns 400, correlation header present")
    void startQuickDeploy_validationFailure_returns400AndCorrelationHeaderPresent() throws Exception {
        QuickDeployRequest badRequest = new QuickDeployRequest(null, "pipeline-trace-001", "step-trace-01", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-003")
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, "trace-test-cid-003"));
    }

    // ---- Helpers ----

    private QuickDeployRequest validStartRequest() {
        return new QuickDeployRequest(
                "trace-deploy-req-001", "trace-pipeline-001", "trace-step-001", "trace-fallback-001");
    }

    private QuickDeployStopRequest validStopRequest() {
        return new QuickDeployStopRequest("trace-deploy-req-002", "trace-pipeline-002");
    }
}
