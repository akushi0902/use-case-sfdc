package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.security.CallerContextResolver;
import com.opsera.integrator.sfdc.security.ScopeAuthorizer;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for {@link JobExecutionController} legacy quick deploy routes.
 *
 * <p>These tests capture the current behavioral contract — HTTP method, path, status code,
 * response body, validation rules, fallback normalization, and service delegation — so that
 * later observability, metrics, and instrumentation changes can prove legacy compatibility.
 *
 * <p><b>Legacy compatibility contract (must remain stable):</b>
 * <ul>
 *   <li>POST /quickdeploy with valid body → 200 "SUCCESS" (text/plain)</li>
 *   <li>POST /quickdeploy missing deploymentRequestId → 400 VALIDATION_FAILED; service NOT called</li>
 *   <li>POST /quickdeploy with null fallbackTaskId → normalized to "" before service call</li>
 *   <li>POST /quickdeploy/stop with any body → 200 "SUCCESS" (cancellation delegation)</li>
 * </ul>
 *
 * <p>Fixtures loaded from {@code fixtures/controllers/}. No real Salesforce, Kafka, or database.
 *
 * <p>See: {@code fixtures/contracts/legacy-release/legacy-contract-inventory.json} LEGACY-001/LEGACY-002.
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("Characterization — JobExecutionController legacy quick deploy behavior")
class JobExecutionControllerCharacterizationTest {

    private static final String QUICK_DEPLOY_URL = "/quickdeploy";
    private static final String QUICK_DEPLOY_STOP_URL = "/quickdeploy/stop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuickDeployService quickDeployService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeLogger;

    @MockBean
    private AuditEventWriter auditEventWriter;

    @MockBean
    private ShellArgumentValidator shellArgumentValidator;

    @MockBean
    private CallerContextResolver callerContextResolver;

    @MockBean
    private ScopeAuthorizer scopeAuthorizer;

    @MockBean
    private TraceContextPropagation traceContextPropagation;

    @MockBean
    private ReleaseLifecycleMetrics releaseLifecycleMetrics;

    @BeforeEach
    void stubTracing() {
        lenient().when(traceContextPropagation.startSpan(anyString()))
                .thenReturn(TraceContextPropagation.SpanInScope.NOOP);
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: Quick deploy success path
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /quickdeploy — success path (CHAR-001)")
    class QuickDeploySuccess {

        @Test
        @DisplayName("valid request → 200 SUCCESS (legacy plain-text acknowledgement)")
        void validRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request → service.start() called exactly once")
        void validRequest_serviceCalledOnce() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(quickDeployService).start(any());
        }

        @Test
        @DisplayName("response is plain-text SUCCESS, not v2 JSON envelope")
        void validRequest_responseIsPlainText() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/plain"))
                    .andExpect(content().string("SUCCESS"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: Missing deploymentRequestId rejection
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /quickdeploy — missing deploymentRequestId rejection (CHAR-002)")
    class QuickDeployMissingId {

        @Test
        @DisplayName("absent deploymentRequestId → 400 VALIDATION_FAILED")
        void missingDeploymentRequestId_returns400() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pipelineId\":\"char-pipeline-001\",\"stepId\":\"char-step-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("absent deploymentRequestId → service.start() never called")
        void missingDeploymentRequestId_serviceNotCalled() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pipelineId\":\"char-pipeline-001\"}"))
                    .andExpect(status().isBadRequest());

            verify(quickDeployService, never()).start(any());
        }

        @Test
        @DisplayName("blank deploymentRequestId → 400 VALIDATION_FAILED; service not called")
        void blankDeploymentRequestId_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deploymentRequestId\":\"\",\"pipelineId\":\"char-pipeline-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

            verify(quickDeployService, never()).start(any());
        }

        @Test
        @DisplayName("malformed JSON → 400 MALFORMED_REQUEST_BODY; service not called")
        void malformedJson_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not valid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

            verify(quickDeployService, never()).start(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: fallbackTaskId normalization behavior
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /quickdeploy — fallbackTaskId normalization (CHAR-002)")
    class FallbackTaskIdNormalization {

        @Test
        @DisplayName("null fallbackTaskId is normalized to empty string before service call")
        void nullFallbackTaskId_normalizedToEmpty() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-request-no-fallback.json");
            body = stripCommentFields(body);

            ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);

            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(quickDeployService).start(captor.capture());
            assertThat(captor.getValue().getFallbackTaskId())
                    .as("fallbackTaskId should be normalized to empty string when absent from request")
                    .isEqualTo("");
        }

        @Test
        @DisplayName("explicit fallbackTaskId is passed through unchanged")
        void explicitFallbackTaskId_passedThrough() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-request.json");
            body = stripCommentFields(body);

            ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);

            mockMvc.perform(post(QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(quickDeployService).start(captor.capture());
            assertThat(captor.getValue().getFallbackTaskId())
                    .as("explicit fallbackTaskId should not be altered by the controller")
                    .isEqualTo("char-fallback-task-001");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-1: Cancellation delegation (stop route)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /quickdeploy/stop — cancellation delegation (CHAR-003)")
    class QuickDeployStop {

        @Test
        @DisplayName("stop request → 200 SUCCESS (legacy cancellation contract)")
        void stopRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/quick-deploy-stop-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(QUICK_DEPLOY_STOP_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("empty stop body → 200 SUCCESS (stop accepts empty body)")
        void emptyStopBody_returns200Success() throws Exception {
            mockMvc.perform(post(QUICK_DEPLOY_STOP_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static String loadFixture(String classpathPath) {
        try (InputStream is = JobExecutionControllerCharacterizationTest.class
                .getClassLoader().getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new AssertionError("Fixture not found on classpath: " + classpathPath
                        + " — add it under src/test/resources/" + classpathPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to load fixture: " + classpathPath, e);
        }
    }

    private static String stripCommentFields(String json) {
        return json.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");
    }
}
