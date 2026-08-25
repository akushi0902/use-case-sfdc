package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Spring MVC validation tests for the {@code POST /quickdeploy} boundary (WO-121).
 *
 * <p>Covers all seven acceptance criteria for quick deploy request validation:
 * <ul>
 *   <li>AC-1: missing deploymentRequestId → HTTP 400, service not called</li>
 *   <li>AC-2: blank/whitespace deploymentRequestId → HTTP 400, service not called</li>
 *   <li>AC-3: valid request → fallbackTaskId canonicalized, service called exactly once</li>
 *   <li>AC-4: valid request → existing SUCCESS response shape preserved</li>
 *   <li>AC-6: MVC tests for all six request scenarios</li>
 *   <li>AC-7: fixtures committed for all six scenarios</li>
 * </ul>
 *
 * <p>Fixtures are under {@code src/test/resources/fixtures/quickdeploy-validation/}.
 * All identifiers are synthetic — no real credentials, org IDs, or Salesforce tokens.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>{@code deploymentRequestId} is enforced by {@code @NotBlank} on
 *       {@link QuickDeployRequest} and {@code @Valid} on the controller parameter.</li>
 *   <li>{@code fallbackTaskId} is normalized to {@code ""} by the controller when
 *       the caller omits it — downstream code never receives {@code null}.</li>
 *   <li>The WO-121 description references {@code taskId}/{@code gitTaskId} field names;
 *       the actual DTO uses {@code fallbackTaskId} for the equivalent canonicalization.</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Quick Deploy Request Validation (WO-121)")
class JobExecutionControllerQuickDeployValidationTest {

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

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/quickdeploy-validation/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── AC-1: missing deploymentRequestId ─────────────────────────────────────

    @Test
    @DisplayName("AC-1: missing deploymentRequestId returns HTTP 400 VALIDATION_FAILED — service not called")
    void startQuickDeploy_missingDeploymentRequestId_returns400_serviceNotCalled() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("missing-deploy-request-id.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"))
                .andExpect(jsonPath("$.fieldErrors[0].safeMessage").value("deploymentRequestId is required"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(quickDeployService, never()).start(any());
    }

    // ── AC-2: blank deploymentRequestId ───────────────────────────────────────

    @Test
    @DisplayName("AC-2: blank deploymentRequestId returns HTTP 400 VALIDATION_FAILED — service not called")
    void startQuickDeploy_blankDeploymentRequestId_returns400_serviceNotCalled() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("blank-deploy-request-id.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"))
                .andExpect(jsonPath("$.correlationId").exists());

        verify(quickDeployService, never()).start(any());
    }

    // ── AC-3 + AC-4: valid request with fallbackTaskId present ────────────────

    @Test
    @DisplayName("AC-3/4: valid request with fallbackTaskId — service called once, fallbackTaskId preserved, SUCCESS returned")
    void startQuickDeploy_validWithFallbackTaskId_serviceCalledOnceWithPreservedFallback() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("valid-quick-deploy-with-fallback.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService, times(1)).start(captor.capture());

        QuickDeployRequest delegated = captor.getValue();
        assertThat(delegated.getDeploymentRequestId())
                .as("deploymentRequestId must be passed through unchanged")
                .isEqualTo("deploy-req-val-001");
        assertThat(delegated.getFallbackTaskId())
                .as("provided fallbackTaskId must not be overwritten by the controller")
                .isEqualTo("fallback-task-val-001");
    }

    // ── AC-3: fallbackTaskId normalization (null → empty string) ──────────────

    @Test
    @DisplayName("AC-3: absent fallbackTaskId is canonicalized to empty string before service dispatch")
    void startQuickDeploy_nullFallbackTaskId_normalizedToEmptyBeforeDispatch() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("valid-quick-deploy-null-fallback.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService, times(1)).start(captor.capture());

        assertThat(captor.getValue().getFallbackTaskId())
                .as("Controller must normalize null fallbackTaskId to empty string to prevent downstream NPE")
                .isEqualTo("");
    }

    @Test
    @DisplayName("AC-3: service is invoked exactly once for each valid request — no double dispatch")
    void startQuickDeploy_validRequest_serviceInvokedExactlyOnce() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("valid-quick-deploy-with-fallback.json")))
                .andExpect(status().isOk());

        verify(quickDeployService, times(1)).start(any(QuickDeployRequest.class));
    }

    // ── AC-6: malformed JSON ──────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6: malformed JSON body returns HTTP 400 MALFORMED_REQUEST_BODY — service not called")
    void startQuickDeploy_malformedJson_returns400MalformedRequestBody_serviceNotCalled() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("malformed-json-body.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.correlationId").exists());

        verify(quickDeployService, never()).start(any());
    }

    // ── AC-6: empty body ──────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6: empty request body returns HTTP 400 MISSING_REQUEST_BODY — service not called")
    void startQuickDeploy_emptyBody_returns400MissingRequestBody_serviceNotCalled() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUEST_BODY"));

        verify(quickDeployService, never()).start(any());
    }

    // ── AC-4: legacy response shape verification ──────────────────────────────

    @Test
    @DisplayName("AC-4: valid request returns exactly 'SUCCESS' plain string body with HTTP 200")
    void startQuickDeploy_validRequest_returnsLegacySuccessShape() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("valid-quick-deploy-with-fallback.json")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("SUCCESS"));
    }
}
