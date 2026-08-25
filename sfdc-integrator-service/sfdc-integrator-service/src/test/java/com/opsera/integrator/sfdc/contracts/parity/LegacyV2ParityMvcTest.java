package com.opsera.integrator.sfdc.contracts.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.controller.JobExecutionController;
import com.opsera.integrator.sfdc.controller.SfdcIntegratorController;
import com.opsera.integrator.sfdc.controller.v2.ReleaseJobController;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.exceptions.V2UnsupportedOperationException;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.observability.TraceContextPropagation;
import com.opsera.integrator.sfdc.observability.metrics.ReleaseLifecycleMetrics;
import com.opsera.integrator.sfdc.resources.v2.deploy.DeploymentSubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.quickdeploy.QuickDeploySubmissionAdapter;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.resources.v2.validate.ValidationSubmissionAdapter;
import com.opsera.integrator.sfdc.security.CallerContextResolver;
import com.opsera.integrator.sfdc.security.ScopeAuthorizer;
import com.opsera.integrator.sfdc.security.ShellArgumentValidator;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract parity MVC tests comparing legacy and v2 release route behavior (WO-153 AC-2, AC-5, AC-6).
 *
 * <p>Each test pair exercises a legacy endpoint and its v2 equivalent, asserting that
 * the v2 route preserves operational intent (same underlying work is dispatched)
 * while intentionally improving the acknowledgement and status contracts.
 *
 * <p>Intentional differences documented in migration-evidence-report.json:
 * <ul>
 *   <li>HTTP 200 (legacy) → HTTP 202 (v2)</li>
 *   <li>plain text body (legacy) → AcceptedAcknowledgement JSON (v2)</li>
 *   <li>no Location header (legacy) → Location header with statusUrl (v2)</li>
 *   <li>CANCEL: HTTP 200 (legacy /quickdeploy/stop) → HTTP 422 (v2 unsupported)</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = {
        JobExecutionController.class,
        SfdcIntegratorController.class,
        ReleaseJobController.class
})
@Import(SfdcExceptionHandler.class)
class LegacyV2ParityMvcTest {

    private static final String V2_ENDPOINT = "/api/v2/sfdc/release-jobs";
    private static final String TEST_JOB_ID = "job-parity-test-001";
    private static final String TEST_CID = "cid-parity-test-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // JobExecutionController deps
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
    private CallerContextResolver callerContextResolver;

    @MockBean
    private ScopeAuthorizer scopeAuthorizer;

    @MockBean
    private TraceContextPropagation traceContextPropagation;

    @MockBean
    private ReleaseLifecycleMetrics releaseLifecycleMetrics;

    // SfdcIntegratorController deps
    @MockBean
    private SfdcIntegratorService sfdcIntegratorService;

    // ReleaseJobController deps
    @MockBean
    private ReleaseCommandFacade releaseCommandFacade;

    @MockBean
    private V2ReleaseRoutesProperties routesProperties;

    @MockBean
    private ReleaseCoexistenceTelemetry releaseCoexistenceTelemetry;

    @MockBean
    private QuickDeploySubmissionAdapter quickDeploySubmissionAdapter;

    @MockBean
    private DeploymentSubmissionAdapter deploymentSubmissionAdapter;

    @MockBean
    private ValidationSubmissionAdapter validationSubmissionAdapter;

    @BeforeEach
    void setUp() {
        lenient().when(traceContextPropagation.startSpan(anyString()))
                .thenReturn(TraceContextPropagation.SpanInScope.NOOP);
        lenient().when(routesProperties.isEnabled()).thenReturn(true);
        lenient().when(routesProperties.isOperationEnabled(anyString())).thenReturn(true);
    }

    // ---- PARITY-QD-001: Quick Deploy ----

    @Test
    void quickDeploy_legacyReturns200Success_v2Returns202WithAck() throws Exception {
        QuickDeployRequest legacyReq = new QuickDeployRequest(
                "deploy-req-parity-qd-001", "pipeline-parity-001", "step-parity-001", "fallback-parity-001");

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.QUICK_DEPLOY));

        ReleaseCommandRequest v2Req = buildCommandRequest(ReleaseOperationType.QUICK_DEPLOY);

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2Req)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").value(TEST_JOB_ID))
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.operationType").value("QUICK_DEPLOY"));
    }

    @Test
    void quickDeploy_legacyDelegatesWork_v2DelegatesViaFacade() throws Exception {
        QuickDeployRequest legacyReq = new QuickDeployRequest(
                "deploy-req-parity-qd-001", "pipeline-parity-001", "step-parity-001", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyReq)))
                .andExpect(status().isOk());
        verify(quickDeployService).start(any(QuickDeployRequest.class));

        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.QUICK_DEPLOY));
        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.QUICK_DEPLOY))))
                .andExpect(status().isAccepted());
        verify(releaseCommandFacade).accept(any(ReleaseCommandRequest.class));
    }

    // ---- PARITY-D-001: Deploy ----

    @Test
    void deploy_legacyReturns200Success_v2Returns202WithAck() throws Exception {
        DeployRequest legacyReq = new DeployRequest("pipeline-parity-deploy-001", "step-deploy-parity-001", null);

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.DEPLOY));

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.DEPLOY))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("DEPLOY"))
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
    }

    // ---- PARITY-V-001: Validate ----

    @Test
    void validate_legacyReturns200Success_v2Returns202WithAck() throws Exception {
        DeployRequest legacyReq = new DeployRequest("pipeline-parity-validate-001", "step-validate-parity-001", null);

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.VALIDATE));

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.VALIDATE))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationType").value("VALIDATE"))
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
    }

    // ---- PARITY-C-001: Cancel (intentional divergence) ----

    @Test
    void cancel_legacyStopReturns200_v2CancelReturns422() throws Exception {
        QuickDeployStopRequest stopReq = new QuickDeployStopRequest();

        mockMvc.perform(post("/quickdeploy/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stopReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        doThrow(new V2UnsupportedOperationException("CANCEL"))
                .when(releaseCommandFacade).accept(any());

        ReleaseCommandRequest cancelReq = buildCommandRequest(ReleaseOperationType.CANCEL);

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_OPERATION"));
    }

    // ---- AC-5: Validation parity — both routes return 400 for missing required fields ----

    @Test
    void validation_legacyAndV2_bothReturn400WithStructuredError() throws Exception {
        QuickDeployRequest badLegacy = new QuickDeployRequest(
                "", "pipeline-parity-001", "step-parity-001", null);
        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLegacy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").isNotEmpty());

        ReleaseCommandRequest badV2 = new ReleaseCommandRequest();
        badV2.setOperationType(ReleaseOperationType.QUICK_DEPLOY);
        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badV2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").isNotEmpty());
    }

    // ---- AC-5: V2 disabled-route returns 503 ----

    @Test
    void v2DisabledRoute_returns503WithStructuredError() throws Exception {
        when(routesProperties.isEnabled()).thenReturn(false);

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.QUICK_DEPLOY))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").isNotEmpty());

        verify(releaseCommandFacade, never()).accept(any());
    }

    // ---- AC-6: Log safety — SafeStructuredLogger must not receive credential fragments ----

    @Test
    void logSafety_noCredentialFragmentsInSafeLoggerCalls() throws Exception {
        QuickDeployRequest req = new QuickDeployRequest(
                "deploy-req-parity-sec-001", "pipeline-parity-sec-001", "step-parity-sec-001", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        ArgumentCaptor<Object> logCaptor = ArgumentCaptor.forClass(Object.class);
        verify(safeStructuredLogger).logEvent(logCaptor.capture());
        String loggedStr = logCaptor.getValue().toString().toLowerCase();

        assertThat(loggedStr).doesNotContain("password")
                .doesNotContain("token")
                .doesNotContain("secret")
                .doesNotContain("apikey")
                .doesNotContain("credential")
                .doesNotContain("authorization")
                .doesNotContain("cookie");
    }

    // ---- AC-2: V2 acknowledges with required fields ----

    @Test
    void v2Ack_containsRequiredFields() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.QUICK_DEPLOY));

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.QUICK_DEPLOY))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.operationType").value("QUICK_DEPLOY"))
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
    }

    // ---- AC-2: Telemetry — both routes emit bounded metric tags ----

    @Test
    void telemetry_legacyRoute_emitsReleaseLifecycleMetrics() throws Exception {
        QuickDeployRequest legacyReq = new QuickDeployRequest(
                "deploy-req-telemetry-001", "pipeline-telemetry-001", "step-telemetry-001", null);

        mockMvc.perform(post("/quickdeploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyReq)))
                .andExpect(status().isOk());

        verify(releaseLifecycleMetrics).recordAcceptanceAttempt(
                ReleaseLifecycleMetrics.OP_QUICK_DEPLOY, ReleaseLifecycleMetrics.OUTCOME_ACCEPTED);
    }

    @Test
    void telemetry_v2Route_emitsReleaseCoexistenceTelemetry() throws Exception {
        when(releaseCommandFacade.accept(any())).thenReturn(buildAck(ReleaseOperationType.QUICK_DEPLOY));

        mockMvc.perform(post(V2_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCommandRequest(ReleaseOperationType.QUICK_DEPLOY))))
                .andExpect(status().isAccepted());

        verify(releaseCoexistenceTelemetry).record(
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ---- Helpers ----

    private AcceptedAcknowledgement buildAck(ReleaseOperationType opType) {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(TEST_JOB_ID);
        ack.setCorrelationId(TEST_CID);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + TEST_JOB_ID + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2026-08-25T00:00:00Z"));
        ack.setOperationType(opType);
        return ack;
    }

    private ReleaseCommandRequest buildCommandRequest(ReleaseOperationType opType) {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(opType);
        req.setCustomerId("customer-parity-001");
        req.setSfdcToolId("sfdc-tool-parity-001");
        req.setTaskId("step-parity-001");
        req.setPipelineId("pipeline-parity-001");
        req.setStepId("step-parity-001");
        return req;
    }
}
