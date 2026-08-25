package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.deploy.DeploymentSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.resources.v2.validate.ValidationSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.validate.ValidationSubmissionRequest;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC controller tests for the dedicated v2 validation endpoint
 * {@code POST /api/v2/sfdc/release-jobs/validate} (WO-148).
 *
 * <p>Covers acceptance criteria:
 * <ul>
 *   <li>AC-1: valid request returns 202 with jobId, correlationId, statusUrl, state, operationType=VALIDATE, acceptedAt</li>
 *   <li>AC-2: missing required fields return 400 with structured error; facade not called</li>
 *   <li>AC-3: prevalidation warnings propagated in response when source control context absent</li>
 *   <li>AC-4: legacy routes unaffected (characterized separately)</li>
 *   <li>AC-6: Spring MVC integration tests</li>
 *   <li>AC-7: fixture-backed tests</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobController.class)
@Import(SfdcExceptionHandler.class)
class ValidationV2ControllerTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs/validate";
    private static final String TEST_JOB_ID = "job-vv2-test-001";
    private static final String TEST_CID = "cid-vv2-test-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReleaseCommandFacade releaseCommandFacade;

    @MockBean
    private V2ReleaseRoutesProperties routesProperties;

    @MockBean
    private SafeStructuredLogger safeLogger;

    @MockBean
    private ReleaseCoexistenceTelemetry telemetry;

    @MockBean
    private QuickDeploySubmissionAdapter quickDeployAdapter;

    @MockBean
    private DeploymentSubmissionAdapter deploymentAdapter;

    @MockBean
    private ValidationSubmissionAdapter validationAdapter;

    @BeforeEach
    void setUp() {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("VALIDATE")).thenReturn(true);
        when(routesProperties.isOperationEnabled(anyString())).thenReturn(true);
        when(validationAdapter.toReleaseCommandRequest(any())).thenReturn(new ReleaseCommandRequest());
        when(validationAdapter.collectPrevalidationWarnings(any())).thenReturn(Collections.emptyList());
    }

    // ---- AC-1: Valid submission returns 202 ----

    @Test
    void submitValidation_validRequest_returns202WithAcknowledgement() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(TEST_JOB_ID))
                .andExpect(jsonPath("$.correlationId").value(TEST_CID))
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.operationType").value("VALIDATE"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.statusUrl").value(containsString(TEST_JOB_ID)));
    }

    @Test
    void submitValidation_validRequest_returnsLocationHeader() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        "/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status"));
    }

    // ---- AC-1 + AC-7: fixture-backed valid package test ----

    @Test
    void submitValidation_packageFixture_returns202() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        String body = new ClassPathResource(
                "fixtures/validate/v2-validate-package-request.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    // ---- AC-7: fixture-backed component-list test ----

    @Test
    void submitValidation_componentListFixture_returns202() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        String body = new ClassPathResource(
                "fixtures/validate/v2-validate-component-list-request.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    // ---- AC-2: Missing required fields return 400 ----

    @Test
    void submitValidation_missingCustomerId_returns400() throws Exception {
        ValidationSubmissionRequest req = validRequest();
        req.setCustomerId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitValidation_missingSfdcToolId_returns400() throws Exception {
        ValidationSubmissionRequest req = validRequest();
        req.setSfdcToolId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitValidation_missingTaskId_returns400() throws Exception {
        ValidationSubmissionRequest req = validRequest();
        req.setTaskId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("taskId"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitValidation_missingTargetOrgId_returns400() throws Exception {
        ValidationSubmissionRequest req = validRequest();
        req.setTargetOrgId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("targetOrgId"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitValidation_missingTargetOrgFixture_returns400() throws Exception {
        String body = new ClassPathResource(
                "fixtures/validate/v2-validate-missing-target-org.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    // ---- AC-3: Prevalidation warnings included in 202 response ----

    @Test
    void submitValidation_missingSourceControlContext_returns202WithWarnings() throws Exception {
        List<String> warnings = List.of(
                "repositoryId not provided; source control traceability will be limited",
                "branch not provided; validation branch context will not be recorded",
                "packageId not provided; component selection will use tool default");
        when(validationAdapter.collectPrevalidationWarnings(any())).thenReturn(warnings);

        AcceptedAcknowledgement ack = acceptedAck();
        ack.setSafeWarnings(warnings);
        when(releaseCommandFacade.accept(any())).thenReturn(ack);

        ValidationSubmissionRequest req = validRequest();
        req.setRepositoryId(null);
        req.setBranch(null);
        req.setPackageId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.safeWarnings", hasSize(3)));
    }

    // ---- Disabled route returns 422 ----

    @Test
    void submitValidation_routesDisabled_returns422() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_ROUTES_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitValidation_validateOperationDisabled_returns422() throws Exception {
        when(routesProperties.isOperationEnabled("VALIDATE")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_OPERATION_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    // ---- Helpers ----

    private ValidationSubmissionRequest validRequest() {
        ValidationSubmissionRequest req = new ValidationSubmissionRequest();
        req.setCustomerId("customer-vv2-001");
        req.setSfdcToolId("sfdc-tool-vv2-001");
        req.setTaskId("step-vv2-001");
        req.setTargetOrgId("org-vv2-001");
        req.setRepositoryId("repo-vv2-001");
        req.setBranch("main");
        req.setPackageId("package-vv2-001");
        req.setValidationMode("PACKAGE");
        req.setPipelineId("pipeline-vv2-001");
        req.setStepId("step-vv2-001");
        return req;
    }

    private AcceptedAcknowledgement acceptedAck() {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(TEST_JOB_ID);
        ack.setCorrelationId(TEST_CID);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2026-08-25T10:00:00Z"));
        ack.setOperationType(ReleaseOperationType.VALIDATE);
        return ack;
    }
}
