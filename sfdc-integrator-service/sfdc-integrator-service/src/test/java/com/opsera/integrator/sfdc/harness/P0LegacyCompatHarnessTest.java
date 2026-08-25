package com.opsera.integrator.sfdc.harness;

import com.opsera.integrator.sfdc.controller.JobExecutionController;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 Legacy Compatibility Harness — ensures legacy release routes keep their
 * prior status codes and response shapes during the v2 coexistence migration window.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /quickdeploy — valid request returns 200 SUCCESS (legacy contract)</li>
 *   <li>POST /quickdeploy — missing deploymentRequestId returns 400 (boundary guard)</li>
 *   <li>POST /quickdeploy/stop — returns 200 SUCCESS (legacy stop contract)</li>
 * </ul>
 *
 * <p>No live Salesforce, Kafka, or PostgreSQL is required. All dependencies are mocked.
 * Fixtures loaded from {@code fixtures/p0-harness/}.
 *
 * <p>To run locally:
 * <pre>
 *   ./gradlew test --tests 'com.opsera.integrator.sfdc.harness.P0LegacyCompatHarnessTest'
 * </pre>
 * See also: {@link P0ReleaseWorkflowHarnessTest} for v2 route coverage.
 */
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@WithMockUser
@DisplayName("P0 Legacy Compat Harness — legacy release route contract regression")
class P0LegacyCompatHarnessTest {

    private static final String LEGACY_QUICK_DEPLOY_URL      = "/quickdeploy";
    private static final String LEGACY_QUICK_DEPLOY_STOP_URL = "/quickdeploy/stop";

    @Autowired
    private MockMvc mockMvc;

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
    // AC-2: Legacy quick deploy — keeps 200 SUCCESS response shape
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy POST /quickdeploy — LEGACY-001 contract regression")
    class LegacyQuickDeploy {

        @Test
        @DisplayName("valid request with deploymentRequestId → 200 SUCCESS (legacy contract)")
        void validLegacyQuickDeploy_returns200Success() throws Exception {
            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-legacy-quick-deploy-request.json");
            // Strip the _comment field so it's valid for legacy DTO deserialization
            body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                       .replaceAll("\\{\\s*,", "{");

            mockMvc.perform(post(LEGACY_QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("missing deploymentRequestId → 400 (boundary guard preserved in legacy route)")
        void missingDeploymentRequestId_returns400_noServiceCall() throws Exception {
            mockMvc.perform(post(LEGACY_QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pipelineId\":\"p0-pipeline\"}"))
                    .andExpect(status().isBadRequest());

            verify(quickDeployService, never()).start(any());
        }

        @Test
        @DisplayName("legacy route must NOT be normalized to v2 contract — stays 200 text/plain SUCCESS")
        void legacyRoute_responseIsPlainTextSuccess_notV2Json() throws Exception {
            String body = P0WorkflowTestSupport.loadFixture("fixtures/p0-harness/p0-legacy-quick-deploy-request.json");
            body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                       .replaceAll("\\{\\s*,", "{");

            mockMvc.perform(post(LEGACY_QUICK_DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/plain"))
                    .andExpect(content().string("SUCCESS"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: Legacy quick deploy stop — keeps 200 SUCCESS response shape
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy POST /quickdeploy/stop — LEGACY-002 contract regression")
    class LegacyQuickDeployStop {

        @Test
        @DisplayName("stop request → 200 SUCCESS (legacy stop contract)")
        void legacyStop_returns200Success() throws Exception {
            mockMvc.perform(post(LEGACY_QUICK_DEPLOY_STOP_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }
    }
}
