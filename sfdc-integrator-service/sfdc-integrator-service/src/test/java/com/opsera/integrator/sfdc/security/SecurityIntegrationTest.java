package com.opsera.integrator.sfdc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.controller.JobExecutionController;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for gateway caller identity resolution.
 *
 * <p>Uses the REAL {@link SecurityConfig} filter chain (imported explicitly) to verify
 * that:
 * <ul>
 *   <li>Authenticated requests with a valid JWT principal reach controller logic (AC-1, AC-6)</li>
 *   <li>Unauthenticated requests are rejected with HTTP 401 before service dispatch (AC-2)</li>
 *   <li>Authentication failure responses include correlationId, errorCode, and remediation (AC-2)</li>
 *   <li>Service logic is never invoked for unauthenticated requests (AC-2)</li>
 * </ul>
 *
 * <p>A stub {@link JwtDecoder} is provided by {@link TestJwtDecoderConfig} so no live
 * identity provider is required. Authenticated requests use
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} which injects a
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * directly into the security context without invoking the decoder.
 */
@WebMvcTest(controllers = JobExecutionController.class)
@Import({SecurityConfig.class, SfdcExceptionHandler.class})
class SecurityIntegrationTest {

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

    // ---- Unauthenticated requests are rejected before service logic ----

    @Test
    void unauthenticatedRequest_startQuickDeploy_returns401() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-safe-001", "pipeline-safe", "step-safe", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(quickDeployService, never()).start(any());
    }

    @Test
    void unauthenticatedRequest_stopQuickDeploy_returns401() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentRequestId\":\"deploy-req-safe-001\"}"))
                .andExpect(status().isUnauthorized());

        verify(quickDeployService, never()).stop(any());
    }

    // ---- 401 response includes structured error fields ----

    @Test
    void unauthenticatedRequest_401responseHasErrorCode() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentRequestId\":\"deploy-req-safe-001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.remediation").isNotEmpty());
    }

    // ---- Authenticated requests reach controller logic ----

    @Test
    void authenticatedRequest_withValidJwtSubject_reaches200() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-safe-001", "pipeline-safe", "step-safe", null);

        mockMvc.perform(post("/quickdeploy")
                        .with(jwt().jwt(j -> j
                                .subject("test-caller-001")
                                .claim("scope", "quick-deploy")
                                .claim("aud", java.util.List.of("sfdc-integrator"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(quickDeployService).start(any());
    }

    @Test
    void authenticatedRequest_stopEndpoint_reaches200() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .with(jwt().jwt(j -> j
                                .subject("test-caller-002")
                                .claim("scope", "quick-deploy")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentRequestId\":\"deploy-req-safe-001\",\"pipelineId\":\"pipeline-safe-001\"}"))
                .andExpect(status().isOk());

        verify(quickDeployService).stop(any());
    }

    // ---- No credentials at all is rejected ----

    @Test
    void requestWithNoAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentRequestId\":\"deploy-req-safe-001\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Actuator health is permitted without authentication ----

    @Test
    void actuatorHealth_noAuthentication_returns200OrDown() throws Exception {
        // /actuator/health is explicitly permitted; verify it does not return 401
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status != 401, "Actuator health must not require authentication; got: " + status);
                });
    }

    /**
     * Provides a stub {@link JwtDecoder} that throws for any token value.
     * Tests use {@code SecurityMockMvcRequestPostProcessors.jwt()} which bypasses
     * the decoder entirely, so this stub is never invoked during normal test execution.
     * It prevents {@link SecurityConfig#jwtDecoder} from calling a live OIDC endpoint.
     */
    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        public JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("Test stub decoder — use jwt() post-processor in tests");
            };
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
