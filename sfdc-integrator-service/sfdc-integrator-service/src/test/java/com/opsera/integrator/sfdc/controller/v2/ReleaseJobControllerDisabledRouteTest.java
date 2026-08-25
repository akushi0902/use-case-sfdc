package com.opsera.integrator.sfdc.controller.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.V2ReleaseRoutesProperties;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.observability.ReleaseCoexistenceTelemetry;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that disabling {@code sfdc.v2.release.routes.enabled} returns HTTP 422
 * with a structured {@link com.opsera.integrator.sfdc.exceptions.ErrorResponse} without
 * delegating to the facade, so operators can perform canary rollback without affecting
 * legacy endpoint availability.
 *
 * <p>Status code changed from 404 to 422 to signal a controlled operator-disabled state
 * (not "resource not found") while avoiding the server-error semantics of 503.
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobController.class)
@Import(SfdcExceptionHandler.class)
class ReleaseJobControllerDisabledRouteTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs";

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

    @BeforeEach
    void setUp() {
        when(routesProperties.isEnabled()).thenReturn(false);
    }

    @Test
    void submitReleaseCommand_routesDisabled_returns422StructuredErrorWithoutDelegation() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ReleaseJobController.ERROR_CODE_ROUTES_DISABLED))
                .andExpect(jsonPath("$.correlationId").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.remediation").value(containsString("legacy")));

        verify(releaseCommandFacade, never()).accept(any());
    }

    @Test
    void submitReleaseCommand_routesDisabled_responseIncludesRemediationHint() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.remediation").value(containsString("/quickdeploy")));
    }

    @Test
    void submitReleaseCommand_routesDisabled_doesNotDispatchWorkOrCallSalesforce() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity());

        verify(releaseCommandFacade, never()).accept(any());
    }

    private ReleaseCommandRequest validRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.DEPLOY);
        req.setCustomerId("customer-001");
        req.setSfdcToolId("sfdc-tool-001");
        req.setTaskId("step-001");
        return req;
    }
}
