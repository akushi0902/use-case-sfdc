package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies per-operation canary gating in {@link ReleaseJobController}.
 *
 * <p>Covers: per-operation allow-list evaluation, default-deny for unknown types,
 * global-disable override when per-operation flags are enabled, and structured error
 * responses for denied operations.
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobController.class)
@Import(SfdcExceptionHandler.class)
class ReleaseJobControllerCanaryTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs";
    private static final String TEST_JOB_ID = "canary-job-001";

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

    @Test
    void submitReleaseCommand_enabledOperation_returns202() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("QUICK_DEPLOY")).thenReturn(true);
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck(ReleaseOperationType.QUICK_DEPLOY));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quickDeployRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("QUICK_DEPLOY"));
    }

    @Test
    void submitReleaseCommand_perOperationDisabled_returns422WithOperationDisabledCode() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("DEPLOY")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deployRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_OPERATION_DISABLED))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.remediation").value(containsString("legacy")));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_globalDisabledOverridesPerOperationEnabled_returns422() throws Exception {
        // Global disabled takes precedence — even if per-operation flag were true,
        // isEnabled()=false means isOperationEnabled() is not consulted.
        when(routesProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quickDeployRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_ROUTES_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_unknownOperationTypeNotInAllowList_returns422() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("CANCEL")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_OPERATION_DISABLED));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_disabledRoute_doesNotDispatchWorkOrCallSalesforce() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("VALIDATE")).thenReturn(false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest())))
                .andExpect(status().isUnprocessableEntity());

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_perOperationEnabled_facadeIsDelegatedTo() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(true);
        when(routesProperties.isOperationEnabled("VALIDATE")).thenReturn(true);
        when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck(ReleaseOperationType.VALIDATE));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("VALIDATE"));

        verify(releaseCommandFacade).accept(any());
    }

    // ---- Helpers ----

    private AcceptedAcknowledgement acceptedAck(ReleaseOperationType operationType) {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(TEST_JOB_ID);
        ack.setCorrelationId("cid-canary-001");
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2026-08-25T10:00:00Z"));
        ack.setOperationType(operationType);
        return ack;
    }

    private ReleaseCommandRequest quickDeployRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        req.setCustomerId("customer-canary-001");
        req.setSfdcToolId("sfdc-tool-canary-001");
        req.setTaskId("step-canary-001");
        req.setPipelineId("pipeline-canary-001");
        req.setStepId("step-canary-001");
        return req;
    }

    private ReleaseCommandRequest deployRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.DEPLOY);
        req.setCustomerId("customer-canary-001");
        req.setSfdcToolId("sfdc-tool-canary-001");
        req.setTaskId("step-deploy-canary-001");
        req.setPipelineId("pipeline-canary-001");
        req.setStepId("step-deploy-canary-001");
        return req;
    }

    private ReleaseCommandRequest validateRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.VALIDATE);
        req.setCustomerId("customer-canary-001");
        req.setSfdcToolId("sfdc-tool-canary-001");
        req.setTaskId("step-validate-canary-001");
        req.setPipelineId("pipeline-canary-001");
        req.setStepId("step-validate-canary-001");
        return req;
    }

    private ReleaseCommandRequest cancelRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.CANCEL);
        req.setCustomerId("customer-canary-001");
        req.setSfdcToolId("sfdc-tool-canary-001");
        req.setTaskId("step-cancel-canary-001");
        return req;
    }
}
