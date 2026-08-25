package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC boundary tests for {@link JobExecutionController} validating the
 * {@code POST /quickdeploy} error contract introduced by WO-114.
 *
 * <p>Fixtures are under {@code src/test/resources/fixtures/boundary-errors/}.
 * All identifiers are synthetic — no real credentials, org IDs, or tokens.
 */
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Boundary Validation (WO-114)")
class JobExecutionControllerBoundaryTest {

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

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/boundary-errors/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── Malformed JSON ────────────────────────────────────────────────────────

    @Test
    @DisplayName("malformed JSON body returns 400 with MALFORMED_REQUEST_BODY errorCode — service not called")
    void startQuickDeploy_malformedJson_returns400StructuredError() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("malformed-quick-deploy-request.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        verify(quickDeployService, never()).start(any());
    }

    // ── Empty body ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("empty request body returns 400 with MISSING_REQUEST_BODY errorCode — service not called")
    void startQuickDeploy_emptyBody_returns400MissingBodyError() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUEST_BODY"));

        verify(quickDeployService, never()).start(any());
    }

    // ── Wrong content type ────────────────────────────────────────────────────

    @Test
    @DisplayName("wrong content type returns 400 with UNSUPPORTED_MEDIA_TYPE errorCode — service not called")
    void startQuickDeploy_wrongContentType_returns400UnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"deploymentRequestId\":\"req-001\",\"pipelineId\":\"p-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));

        verify(quickDeployService, never()).start(any());
    }

    // ── Missing required field ────────────────────────────────────────────────

    @Test
    @DisplayName("missing deploymentRequestId returns 400 with VALIDATION_FAILED and field error — service not called")
    void startQuickDeploy_missingDeploymentId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("missing-deployment-id-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"))
                .andExpect(jsonPath("$.fieldErrors[0].safeMessage").value("deploymentRequestId is required"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedReason").value("field-validation-failed"))
                .andExpect(jsonPath("$.remediation").exists());

        verify(quickDeployService, never()).start(any());
    }

    @Test
    @DisplayName("blank deploymentRequestId returns 400 with VALIDATION_FAILED — service not called")
    void startQuickDeploy_blankDeploymentId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("blank-deployment-id-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deploymentRequestId"));

        verify(quickDeployService, never()).start(any());
    }

    // ── Unknown extra field ───────────────────────────────────────────────────

    @Test
    @DisplayName("request with unknown extra field is accepted — Jackson ignores unknown by default")
    void startQuickDeploy_extraField_isAcceptedAndServiceCalled() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("extra-field-quick-deploy-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }

    // ── Valid request ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid request returns 200 SUCCESS — service called exactly once")
    void startQuickDeploy_validRequest_returns200SuccessAndCallsService() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("valid-quick-deploy-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(quickDeployService).start(any(QuickDeployRequest.class));
    }
}
