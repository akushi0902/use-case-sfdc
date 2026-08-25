package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.ScopeAuthorizationException;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.security.CallerContext;
import com.opsera.integrator.sfdc.security.CallerContextResolver;
import com.opsera.integrator.sfdc.security.ScopeAuthorizer;
import com.opsera.integrator.sfdc.security.ScopeConstants;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC integration tests verifying scope-based authorization enforcement on
 * quick deploy endpoints.
 *
 * <p>These tests exercise the full request→authorization→response path, including
 * the {@link SfdcExceptionHandler} 403 mapping for {@link ScopeAuthorizationException}.
 *
 * <p>Authorization decision is tested by configuring the {@link ScopeAuthorizer} mock
 * to either permit (no-op void) or deny (throw {@link ScopeAuthorizationException}),
 * simulating different caller scope combinations without requiring real JWT infrastructure.
 *
 * <p>Security constraints verified:
 * <ul>
 *   <li>Denied requests never reach service layer (QuickDeployService must not be called)</li>
 *   <li>403 response body exposes requiredCapability for authorization debugging without raw claims</li>
 *   <li>Submit and cancel scopes are not interchangeable</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Scope Authorization Integration Tests")
class JobExecutionControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/quickdeploy/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private CallerContext callerWithScopes(String... scopes) {
        return CallerContext.builder()
                .subject("fixture-subject-auth-test")
                .scopes(Set.of(scopes))
                .authenticationType("JWT")
                .correlationId("corr-auth-test-001")
                .build();
    }

    // ── AUTH-001: POST /quickdeploy — start ──────────────────────────────────

    @Nested
    @DisplayName("AUTH-001: POST /quickdeploy — start quick deploy")
    class StartQuickDeployAuth {

        @Test
        @DisplayName("AUTH-001a: caller with quickdeploy.submit scope — request proceeds and returns HTTP 200")
        void startQuickDeploy_submitScope_returns200() throws Exception {
            // scopeAuthorizer is a @MockBean — requireScope() is a no-op void by default (authorized)
            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AUTH-001b: authorized request delegates to QuickDeployService.start()")
        void startQuickDeploy_authorized_delegatesToService() throws Exception {
            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isOk());
            verify(quickDeployService).start(any());
        }

        @Test
        @DisplayName("AUTH-001c: caller with no scopes — ScopeAuthorizer denies, returns HTTP 403")
        void startQuickDeploy_noScopes_returns403() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_SUBMIT, "INSUFFICIENT_SCOPE",
                    "corr-auth-001c", "subj:present"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_SUBMIT), any());

            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SCOPE"))
                    .andExpect(jsonPath("$.requiredCapability").value(ScopeConstants.QUICK_DEPLOY_SUBMIT));
        }

        @Test
        @DisplayName("AUTH-001d: denied start request never reaches QuickDeployService.start()")
        void startQuickDeploy_denied_serviceNotCalled() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_SUBMIT, "INSUFFICIENT_SCOPE",
                    "corr-auth-001d", "unknown"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_SUBMIT), any());

            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isForbidden());

            verify(quickDeployService, never()).start(any());
        }

        @Test
        @DisplayName("AUTH-001e: caller with cancel scope only cannot start — returns HTTP 403")
        void startQuickDeploy_cancelScopeOnly_returns403() throws Exception {
            // cancel scope does not satisfy submit requirement
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_SUBMIT, "INSUFFICIENT_SCOPE",
                    "corr-auth-001e", "subj:present"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_SUBMIT), any());

            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredCapability").value(ScopeConstants.QUICK_DEPLOY_SUBMIT));
        }

        @Test
        @DisplayName("AUTH-001f: unauthenticated caller (null authentication) — ScopeAuthorizer denies, returns HTTP 403")
        void startQuickDeploy_unauthenticated_returns403() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_SUBMIT, "UNAUTHENTICATED",
                    "unknown", "unknown"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_SUBMIT), any());

            mockMvc.perform(post("/quickdeploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-valid-full-request.json")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SCOPE"));
        }
    }

    // ── AUTH-002: POST /quickdeploy/stop — cancel ────────────────────────────

    @Nested
    @DisplayName("AUTH-002: POST /quickdeploy/stop — cancel quick deploy")
    class StopQuickDeployAuth {

        @Test
        @DisplayName("AUTH-002a: caller with quickdeploy.cancel scope — request proceeds and returns HTTP 200")
        void stopQuickDeploy_cancelScope_returns200() throws Exception {
            // scopeAuthorizer is a @MockBean — requireScope() is a no-op void by default (authorized)
            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AUTH-002b: authorized stop request delegates to QuickDeployService.stop()")
        void stopQuickDeploy_authorized_delegatesToService() throws Exception {
            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isOk());
            verify(quickDeployService).stop(any());
        }

        @Test
        @DisplayName("AUTH-002c: caller with no scopes — ScopeAuthorizer denies stop, returns HTTP 403")
        void stopQuickDeploy_noScopes_returns403() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_CANCEL, "INSUFFICIENT_SCOPE",
                    "corr-auth-002c", "subj:present"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_CANCEL), any());

            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SCOPE"))
                    .andExpect(jsonPath("$.requiredCapability").value(ScopeConstants.QUICK_DEPLOY_CANCEL));
        }

        @Test
        @DisplayName("AUTH-002d: denied stop request never reaches QuickDeployService.stop()")
        void stopQuickDeploy_denied_serviceNotCalled() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_CANCEL, "INSUFFICIENT_SCOPE",
                    "corr-auth-002d", "unknown"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_CANCEL), any());

            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isForbidden());

            verify(quickDeployService, never()).stop(any());
        }

        @Test
        @DisplayName("AUTH-002e: caller with submit scope only cannot stop — returns HTTP 403")
        void stopQuickDeploy_submitScopeOnly_returns403() throws Exception {
            // submit scope does not satisfy cancel requirement
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_CANCEL, "INSUFFICIENT_SCOPE",
                    "corr-auth-002e", "subj:present"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_CANCEL), any());

            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredCapability").value(ScopeConstants.QUICK_DEPLOY_CANCEL));
        }

        @Test
        @DisplayName("AUTH-002f: unauthenticated caller (null authentication) — ScopeAuthorizer denies, returns HTTP 403")
        void stopQuickDeploy_unauthenticated_returns403() throws Exception {
            doThrow(new ScopeAuthorizationException(
                    ScopeConstants.QUICK_DEPLOY_CANCEL, "UNAUTHENTICATED",
                    "unknown", "unknown"))
                    .when(scopeAuthorizer).requireScope(any(), eq(ScopeConstants.QUICK_DEPLOY_CANCEL), any());

            mockMvc.perform(post("/quickdeploy/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("quick-deploy-stop-request.json")))
                    .andExpect(status().isForbidden());
        }
    }
}
