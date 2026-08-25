package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization compatibility tests for the legacy integrator controller surface
 * of {@link SfdcIntegratorController}.
 *
 * <p><strong>Purpose:</strong> These tests lock the observable HTTP contract of
 * {@code POST /deploy} and {@code POST /validate} so that later changes to shared
 * validation, logging, async dispatch abstraction, or exception handling cannot
 * silently alter the semantics that internal pipeline consumers depend on.
 *
 * <p><strong>Contract map:</strong>
 * {@code src/test/resources/contracts/legacy-sfdc-integrator-controller.json}
 * (SFDC-001, SFDC-002). Consult the map before modifying response shapes or
 * service delegation signatures.
 *
 * <p><strong>Review gate:</strong> A failure in this class indicates an accidental
 * change to a locked legacy contract. Any intentional change requires:
 * <ol>
 *   <li>Migration guidance for downstream pipeline consumers.</li>
 *   <li>Updated entries in {@code contracts/legacy-sfdc-integrator-controller.json}.</li>
 *   <li>Updated entries in
 *       {@code fixtures/contracts/legacy-release/legacy-contract-inventory.json}.</li>
 * </ol>
 *
 * <p><strong>Fixture location:</strong>
 * {@code src/test/resources/fixtures/sfdc-integrator/}. All fixtures use synthetic
 * identifiers — no real Salesforce org URLs, customer data, credentials, or tokens.
 *
 * <p><strong>Runtime requirements:</strong> No Salesforce, Kafka, Hazelcast, Kubernetes,
 * Git, S3, or shell execution dependency. All collaborators are mocked.
 */
@WebMvcTest(controllers = SfdcIntegratorController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("SfdcIntegratorController — Legacy Integrator Compatibility")
class SfdcIntegratorControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SfdcIntegratorService sfdcIntegratorService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    // ── Fixture helper ────────────────────────────────────────────────────────

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/sfdc-integrator/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── SFDC-001: POST /deploy ────────────────────────────────────────────────

    @Test
    @DisplayName("SFDC-001: valid deploy request returns HTTP 200 with plain SUCCESS body")
    void deploy_validFullRequest_returns200WithSuccessBody() throws Exception {
        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-deploy-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("SFDC-001: valid deploy request delegates exactly once to SfdcIntegratorService.deploy()")
    void deploy_validFullRequest_delegatesExactlyOnce() throws Exception {
        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-deploy-request.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService, times(1)).deploy(captor.capture());

        DeployRequest delegated = captor.getValue();
        assertThat(delegated.getPipelineId()).isEqualTo("pipeline-compat-sfdc-001");
        assertThat(delegated.getStepId()).isEqualTo("step-compat-deploy-001");
        assertThat(delegated.getOrgUrl()).isEqualTo("https://org.example.internal");
    }

    @Test
    @DisplayName("SFDC-001: service exception on deploy returns HTTP 500 with structured safe body — no stack trace")
    void deploy_serviceThrowsException_returns500WithSafeBody() throws Exception {
        doThrow(new RuntimeException("simulated-deploy-failure"))
                .when(sfdcIntegratorService).deploy(any(DeployRequest.class));

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-deploy-request.json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal service error"));
    }

    @Test
    @DisplayName("SFDC-001: malformed JSON body returns HTTP 400 with structured SfdcExceptionHandler body — service not called")
    void deploy_malformedJson_returns400WithExceptionHandlerBody() throws Exception {
        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

        verify(sfdcIntegratorService, never()).deploy(any());
    }

    // ── SFDC-002: POST /validate ──────────────────────────────────────────────

    @Test
    @DisplayName("SFDC-002: valid validate request returns HTTP 200 with plain SUCCESS body")
    void validate_validFullRequest_returns200WithSuccessBody() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-validate-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("SFDC-002: valid validate request delegates exactly once to SfdcIntegratorService.validate()")
    void validate_validFullRequest_delegatesExactlyOnce() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-validate-request.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<DeployRequest> captor = ArgumentCaptor.forClass(DeployRequest.class);
        verify(sfdcIntegratorService, times(1)).validate(captor.capture());

        DeployRequest delegated = captor.getValue();
        assertThat(delegated.getPipelineId()).isEqualTo("pipeline-compat-sfdc-002");
        assertThat(delegated.getStepId()).isEqualTo("step-compat-validate-001");
        assertThat(delegated.getOrgUrl()).isEqualTo("https://org-validate.example.internal");
    }

    @Test
    @DisplayName("SFDC-002: service exception on validate returns HTTP 500 with structured safe body — no stack trace")
    void validate_serviceThrowsException_returns500WithSafeBody() throws Exception {
        doThrow(new RuntimeException("simulated-validate-failure"))
                .when(sfdcIntegratorService).validate(any(DeployRequest.class));

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("sfdc-integrator-validate-request.json")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal service error"));
    }

    @Test
    @DisplayName("SFDC-002: malformed JSON body returns HTTP 400 with structured SfdcExceptionHandler body — service not called")
    void validate_malformedJson_returns400WithExceptionHandlerBody() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

        verify(sfdcIntegratorService, never()).validate(any());
    }
}
