package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.deploy.DeploymentSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.deploy.DeploymentSubmissionRequest;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.validate.ValidationSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
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
 * Spring MVC controller tests for the dedicated v2 deployment endpoint
 * {@code POST /api/v2/sfdc/release-jobs/deploy} (WO-147).
 *
 * <p>Covers acceptance criteria:
 * <ul>
 *   <li>AC-1: valid request returns 202 with jobId, correlationId, statusUrl, state, operationType, acceptedAt</li>
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
class DeploymentV2ControllerTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs/deploy";
    private static final String TEST_JOB_ID = "job-dv2-test-001";
    private static final String TEST_CID = "cid-dv2-test-001";

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
        when(routesProperties.isOperationEnabled("DEPLOY")).thenReturn(true);
        when(routesProperties.isOperationEnabled(anyString())).thenReturn(true);
        when(deploymentAdapter.toReleaseCommandRequest(any())).thenReturn(new ReleaseCommandRequest());
        when(deploymentAdapter.collectPrevalidationWarnings(any())).thenReturn(Collections.emptyList());
    }

    // ---- AC-1: Valid submission returns 202 ----

    @Test
    void submitDeployment_validRequest_returns202WithAcknowledgement() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(TEST_JOB_ID))
                .andExpect(jsonPath("$.correlationId").value(TEST_CID))
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.operationType").value("DEPLOY"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.statusUrl").value(containsString(TEST_JOB_ID)));
    }

    @Test
    void submitDeployment_validRequest_returnsLocationHeader() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        "/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status"));
    }

    // ---- AC-1: fixture-backed valid test ----

    @Test
    void submitDeployment_validFixture_returns202() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

        String body = new ClassPathResource(
                "fixtures/deploy/v2-deploy-valid-request.json")
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
    void submitDeployment_missingCustomerId_returns400() throws Exception {
        DeploymentSubmissionRequest req = validRequest();
        req.setCustomerId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitDeployment_missingSfdcToolId_returns400() throws Exception {
        DeploymentSubmissionRequest req = validRequest();
        req.setSfdcToolId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitDeployment_missingTaskId_returns400() throws Exception {
        DeploymentSubmissionRequest req = validRequest();
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
    void submitDeployment_missingRequiredContextFixture_returns400() throws Exception {
        String body = new ClassPathResource(
                "fixtures/deploy/v2-deploy-missing-required-context.json")
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
    void submitDeployment_missingSourceControlContext_returns202WithWarnings() throws Exception {
        List<String> warnings = List.of(
                "repositoryId not provided; source control traceability will be limited",
                "branch not provided; deployment branch context will not be recorded",
                "packageId not provided; component selection will use tool default");
        when(deploymentAdapter.collectPrevalidationWarnings(any())).thenReturn(warnings);

        AcceptedAcknowledgement ack = acceptedAck();
        ack.setSafeWarnings(warnings);
        when(releaseCommandFacade.accept(any())).thenReturn(ack);

        String body = new ClassPathResource(
                "fixtures/deploy/v2-deploy-prevalidation-warning.json")
                .getContentAsString(StandardCharsets.UTF_8);
        body = body.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.safeWarnings", hasSize(3)));
    }

    // ---- Disabled route returns 422 ----

    @Test
    void submitDeployment_routesDisabled_returns422() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_ROUTES_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitDeployment_deployOperationDisabled_returns422() throws Exception {
        when(routesProperties.isOperationEnabled("DEPLOY")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_OPERATION_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    // ---- Helpers ----

    private DeploymentSubmissionRequest validRequest() {
        DeploymentSubmissionRequest req = new DeploymentSubmissionRequest();
        req.setCustomerId("customer-dv2-001");
        req.setSfdcToolId("sfdc-tool-dv2-001");
        req.setTaskId("step-dv2-001");
        req.setRepositoryId("repo-dv2-001");
        req.setBranch("main");
        req.setPackageId("package-dv2-001");
        req.setPipelineId("pipeline-dv2-001");
        req.setStepId("step-dv2-001");
        return req;
    }

    private AcceptedAcknowledgement acceptedAck() {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(TEST_JOB_ID);
        ack.setCorrelationId(TEST_CID);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2026-08-25T10:00:00Z"));
        ack.setOperationType(ReleaseOperationType.DEPLOY);
        return ack;
    }
}
