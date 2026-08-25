package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * MVC integration tests verifying that {@link JobExecutionController} emits
 * safe structured log events and never includes raw DTO toString content.
 *
 * <p>Uses a {@code MockBean SafeStructuredLogger} and {@code ArgumentCaptor} so
 * tests can inspect the {@link SafeLogEvent} handed to the logger without needing
 * a log-capture appender.
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Safe Logging (WO-115)")
class JobExecutionControllerSafeLoggingTest {

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

    // ── quick deploy start ────────────────────────────────────────────────────

    @Test
    @DisplayName("startQuickDeploy: safeLogger.logEvent called once with ACCEPTED outcome")
    void startQuickDeploy_validRequest_safeLoggerCalledOnce() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-log-001", "pipeline-log-001", "step-log-qdeploy-01", "fallback-log-001");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
        verify(safeStructuredLogger, times(1)).logEvent(captor.capture());

        SafeLogEvent event = captor.getValue();
        assertThat(event.getOperation()).isEqualTo("quick-deploy-start");
        assertThat(event.getController()).isEqualTo("JobExecutionController");
        assertThat(event.getOutcome()).isEqualTo(SafeLogEvent.Outcome.ACCEPTED);
    }

    @Test
    @DisplayName("startQuickDeploy: safe log event contains pipelineId but NOT raw DTO toString")
    void startQuickDeploy_validRequest_safeFieldsPresentNoDtoToString() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-log-002", "pipeline-log-002", "step-log-02", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
        verify(safeStructuredLogger).logEvent(captor.capture());

        SafeLogEvent event = captor.getValue();
        assertThat(event.getSafeFields()).containsKey("pipelineId");
        assertThat(event.getSafeFields().get("pipelineId")).isEqualTo("pipeline-log-002");
        // Raw DTO toString must not appear as a field value
        event.getSafeFields().values().forEach(v ->
                assertThat(v).doesNotContain("QuickDeployRequest"));
    }

    @Test
    @DisplayName("startQuickDeploy: safe log event does NOT contain fallbackTaskId or raw body")
    void startQuickDeploy_validRequest_sensitiveOrRawFieldsAbsent() throws Exception {
        QuickDeployRequest request = new QuickDeployRequest(
                "deploy-req-log-003", "pipeline-log-003", "step-log-03", "sensitive-fallback");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
        verify(safeStructuredLogger).logEvent(captor.capture());

        SafeLogEvent event = captor.getValue();
        // fallbackTaskId is not an allow-listed field and must not appear
        assertThat(event.getSafeFields()).doesNotContainKey("fallbackTaskId");
        // deploymentRequestId is not included (could be PII-adjacent)
        assertThat(event.getSafeFields()).doesNotContainKey("deploymentRequestId");
    }

    // ── quick deploy stop ─────────────────────────────────────────────────────

    @Test
    @DisplayName("stopQuickDeploy: safeLogger.logEvent called once with ACCEPTED outcome")
    void stopQuickDeploy_validRequest_safeLoggerCalledOnce() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentRequestId\":\"req-stop-001\",\"pipelineId\":\"pipeline-stop-001\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
        verify(safeStructuredLogger, times(1)).logEvent(captor.capture());

        SafeLogEvent event = captor.getValue();
        assertThat(event.getOperation()).isEqualTo("quick-deploy-stop");
        assertThat(event.getOutcome()).isEqualTo(SafeLogEvent.Outcome.ACCEPTED);
    }
}
