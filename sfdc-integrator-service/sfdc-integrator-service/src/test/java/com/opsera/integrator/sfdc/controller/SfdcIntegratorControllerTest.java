package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for {@link SfdcIntegratorController} legacy deployment and
 * validation routes.
 *
 * <p>These tests capture current contract behavior before v2 coexistence changes.
 * They must remain green; a failure indicates an accidental change to legacy behavior.
 *
 * <p>See: {@code src/test/resources/fixtures/contracts/legacy-release/legacy-contract-inventory.json}
 * for the machine-readable contract record (LEGACY-003, LEGACY-004).
 */
@WebMvcTest(controllers = SfdcIntegratorController.class)
@Import(SfdcExceptionHandler.class)
class SfdcIntegratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SfdcIntegratorService sfdcIntegratorService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    // ---- LEGACY-003: POST /deploy ----

    @Test
    void deploy_validRequest_returns200Success() throws Exception {
        // Fixture: deploy-valid-request.json
        DeployRequest request = new DeployRequest(
                "pipeline-003", "step-deploy-01", "https://test.salesforce.example.internal");

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
    }

    @Test
    void deploy_validRequest_delegatesToSfdcIntegratorService() throws Exception {
        DeployRequest request = new DeployRequest(
                "pipeline-003", "step-deploy-01", "https://test.salesforce.example.internal");

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(sfdcIntegratorService).deploy(any(DeployRequest.class));
    }

    @Test
    void deploy_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest());

        verify(sfdcIntegratorService, never()).deploy(any());
    }

    // ---- LEGACY-004: POST /validate ----

    @Test
    void validate_validRequest_returns200Success() throws Exception {
        // Fixture: deploy-valid-request.json (shared DTO)
        DeployRequest request = new DeployRequest(
                "pipeline-003", "step-deploy-01", "https://test.salesforce.example.internal");

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
    }

    @Test
    void validate_validRequest_delegatesToSfdcIntegratorService() throws Exception {
        DeployRequest request = new DeployRequest(
                "pipeline-003", "step-deploy-01", "https://test.salesforce.example.internal");

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(sfdcIntegratorService).validate(any(DeployRequest.class));
    }

    @Test
    void validate_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());

        verify(sfdcIntegratorService, never()).validate(any());
    }
}
