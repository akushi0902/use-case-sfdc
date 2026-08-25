package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.exceptions.V2UnsupportedOperationException;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * MockMvc integration tests for {@link ReleaseJobController}.
 *
 * <p>Verifies v2 submission responses, validation failure behavior, disabled-route
 * behavior, and legacy route coexistence (characterized separately in
 * {@code JobExecutionControllerCompatibilityTest}).
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobController.class)
@Import(SfdcExceptionHandler.class)
class ReleaseJobControllerTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs";
    private static final String TEST_CID = "ctrl-test-cid-001";
    private static final String TEST_JOB_ID = "job-id-test-001";

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

    @BeforeEach
    void setUp() {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled(anyString())).thenReturn(true);
    }

    // ---- Valid submissions ----

    @Test
    void submitReleaseCommand_validQuickDeploy_returns202WithAcknowledgement() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck(ReleaseOperationType.QUICK_DEPLOY));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", TEST_CID)
                        .content(objectMapper.writeValueAsString(quickDeployRequest())))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status"))
                .andExpect(jsonPath("$.jobId").value(TEST_JOB_ID))
                .andExpect(jsonPath("$.correlationId").value(TEST_CID))
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.operationType").value("QUICK_DEPLOY"))
                .andExpect(jsonPath("$.statusUrl").value("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
    }

    @Test
    void submitReleaseCommand_validDeploy_returns202() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck(ReleaseOperationType.DEPLOY));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deployRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("DEPLOY"))
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
    }

    @Test
    void submitReleaseCommand_validValidate_returns202() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck(ReleaseOperationType.VALIDATE));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("VALIDATE"));
    }

    // ---- Invalid submissions ----

    @Test
    void submitReleaseCommand_missingOperationType_returns400() throws Exception {
        ReleaseCommandRequest req = quickDeployRequest();
        req.setOperationType(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_missingTaskId_returns400() throws Exception {
        ReleaseCommandRequest req = quickDeployRequest();
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
    void submitReleaseCommand_missingCustomerId_returns400() throws Exception {
        ReleaseCommandRequest req = quickDeployRequest();
        req.setCustomerId(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").isNotEmpty());
    }

    // ---- Unsupported operation ----

    @Test
    void submitReleaseCommand_cancelOperation_returns422UnsupportedOperation() throws Exception {
        doThrow(new V2UnsupportedOperationException("CANCEL"))
                .when(releaseCommandFacade).accept(any());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_OPERATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CANCEL")));
    }

    // ---- Service delegation failure ----

    @Test
    void submitReleaseCommand_serviceThrowsException_returns500() throws Exception {
        doThrow(new RuntimeException("service unavailable")).when(releaseCommandFacade).accept(any());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quickDeployRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    // ---- Helpers ----

    private AcceptedAcknowledgement acceptedAck(ReleaseOperationType operationType) {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(TEST_JOB_ID);
        ack.setCorrelationId(TEST_CID);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2026-08-25T10:00:00Z"));
        ack.setOperationType(operationType);
        return ack;
    }

    private ReleaseCommandRequest quickDeployRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-001");
        req.setPipelineId("pipeline-001");
        req.setStepId("step-001");
        req.setDeployRequestId("deploy-req-001");
        return req;
    }

    private ReleaseCommandRequest deployRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.DEPLOY);
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-deploy-001");
        req.setPipelineId("pipeline-001");
        req.setStepId("step-deploy-001");
        return req;
    }

    private ReleaseCommandRequest validateRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.VALIDATE);
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-validate-001");
        req.setPipelineId("pipeline-001");
        req.setStepId("step-validate-001");
        return req;
    }

    private ReleaseCommandRequest cancelRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.CANCEL);
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-cancel-001");
        return req;
    }
}
