package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization compatibility tests for the legacy quick deploy boundary in
 * {@link JobExecutionController}.
 *
 * <p><strong>Purpose:</strong> These tests capture the current HTTP contract for
 * {@code POST /quickdeploy} and {@code POST /quickdeploy/stop} so that future
 * changes to shared validation paths, logging, async dispatch, or exception
 * handling cannot accidentally alter the legacy response shape without a
 * deliberate, reviewed migration step.
 *
 * <p><strong>Review gate:</strong> Any change that causes a test in this class to
 * fail must be accompanied by explicit migration guidance for downstream pipeline
 * consumers and an update to
 * {@code fixtures/contracts/legacy-release/legacy-contract-inventory.json}.
 *
 * <p><strong>Fixture location:</strong> {@code src/test/resources/fixtures/quickdeploy/}.
 * Fixtures use only synthetic identifiers — no real customer data, Salesforce org
 * identifiers, credentials, or tokens.
 *
 * <p><strong>Runtime requirements:</strong> No Salesforce credentials, Kafka brokers,
 * Hazelcast clusters, Kubernetes access, or package manager secrets are needed.
 * All service dependencies are mocked.
 *
 * <p>See also:
 * {@code docs/operations/quick-deploy-compat-note.md} — prose description of the
 * preserved behavior and the expected change process.
 */
@WebMvcTest(controllers = JobExecutionController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("JobExecutionController — Quick Deploy Legacy Compatibility")
class JobExecutionControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuickDeployService quickDeployService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private String fixture(String name) throws Exception {
        var resource = new ClassPathResource("fixtures/quickdeploy/" + name);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    // ── LEGACY-001: POST /quickdeploy — start ────────────────────────────────

    @Test
    @DisplayName("LEGACY-001: valid full request returns HTTP 200 with plain SUCCESS body")
    void startQuickDeploy_validFullRequest_returns200WithSuccessBody() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-valid-full-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("LEGACY-001: valid full request delegates exactly once to QuickDeployService.start()")
    void startQuickDeploy_validFullRequest_delegatesExactlyOnce() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-valid-full-request.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService, times(1)).start(captor.capture());

        QuickDeployRequest delegated = captor.getValue();
        assertThat(delegated.getDeploymentRequestId()).isEqualTo("deploy-req-compat-001");
        assertThat(delegated.getFallbackTaskId()).isEqualTo("fallback-task-compat-001");
    }

    @Test
    @DisplayName("LEGACY-001: fallbackTaskId present in request is preserved by controller — not overwritten")
    void startQuickDeploy_fallbackTaskIdPresent_isPreservedInDelegatedRequest() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-valid-full-request.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService).start(captor.capture());

        assertThat(captor.getValue().getFallbackTaskId())
                .as("When fallbackTaskId is provided it must not be overwritten")
                .isEqualTo("fallback-task-compat-001");
    }

    @Test
    @DisplayName("LEGACY-001: absent fallbackTaskId is normalized to empty string before delegation")
    void startQuickDeploy_absentFallbackTaskId_normalizesToEmptyStringBeforeDelegation() throws Exception {
        // Fixture has no fallbackTaskId field; Jackson deserializes it as null.
        // Controller must set it to "" before calling start().
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-no-fallback-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        ArgumentCaptor<QuickDeployRequest> captor = ArgumentCaptor.forClass(QuickDeployRequest.class);
        verify(quickDeployService, times(1)).start(captor.capture());

        assertThat(captor.getValue().getFallbackTaskId())
                .as("Controller must normalize null fallbackTaskId to empty string")
                .isEqualTo("");
    }

    @Test
    @DisplayName("LEGACY-001: missing deploymentRequestId returns HTTP 400 with exact error body")
    void startQuickDeploy_missingDeploymentId_returns400WithExactErrorBody() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-missing-id-request.json")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("deploymentRequestId is required"));

        verify(quickDeployService, never()).start(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("LEGACY-001: blank deploymentRequestId returns HTTP 400 — service not called")
    void startQuickDeploy_blankDeploymentId_returns400BeforeServiceCall() throws Exception {
        QuickDeployRequest blankIdRequest = new QuickDeployRequest(
                "   ", "pipeline-compat-004", "step-compat-004", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankIdRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("deploymentRequestId is required"));

        verify(quickDeployService, never()).start(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("LEGACY-001: malformed JSON body returns HTTP 400 with SfdcExceptionHandler body")
    void startQuickDeploy_malformedJson_returns400WithExceptionHandlerBody() throws Exception {
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Malformed request body"));

        verify(quickDeployService, never()).start(org.mockito.ArgumentMatchers.any());
    }

    // ── LEGACY-002: POST /quickdeploy/stop — cancellation ────────────────────

    @Test
    @DisplayName("LEGACY-002: valid stop request returns HTTP 200 with plain SUCCESS body")
    void stopQuickDeploy_validRequest_returns200WithSuccessBody() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-stop-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @DisplayName("LEGACY-002: valid stop request delegates exactly once to QuickDeployService.stop()")
    void stopQuickDeploy_validRequest_delegatesExactlyOnce() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("quick-deploy-stop-request.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<QuickDeployStopRequest> captor =
                ArgumentCaptor.forClass(QuickDeployStopRequest.class);
        verify(quickDeployService, times(1)).stop(captor.capture());

        assertThat(captor.getValue().getDeploymentRequestId())
                .isEqualTo("deploy-req-compat-001");
        assertThat(captor.getValue().getPipelineId())
                .isEqualTo("pipeline-compat-001");
    }

    @Test
    @DisplayName("LEGACY-002: malformed JSON body returns HTTP 400 with SfdcExceptionHandler body")
    void stopQuickDeploy_malformedJson_returns400WithExceptionHandlerBody() throws Exception {
        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Malformed request body"));

        verify(quickDeployService, never()).stop(org.mockito.ArgumentMatchers.any());
    }
}
