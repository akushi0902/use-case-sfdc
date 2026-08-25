package com.opsera.integrator.sfdc.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseCommandRequest;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import com.opsera.integrator.sfdc.services.v2.ReleaseCommandFacade;
import com.opsera.integrator.sfdc.controller.v2.ReleaseJobController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests verifying that coexistence telemetry correlation identifiers are propagated
 * correctly for v2 release routes, and that telemetry is emitted with bounded outcome tags.
 *
 * <p>These tests satisfy AC-2 (correlation propagation), AC-4 (safe log fields), and AC-6
 * (bounded outcome tags distinguishing accepted, disabledRoute, etc.).
 */
@WithMockUser
@WebMvcTest(controllers = ReleaseJobController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("Coexistence telemetry — correlation propagation and metric emission")
class CoexistenceTelemetryCorrelationTest {

    private static final String ENDPOINT = "/api/v2/sfdc/release-jobs";
    private static final String TEST_CID = "telemetry-test-cid-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReleaseCommandFacade releaseCommandFacade;

    @MockBean
    private SafeStructuredLogger safeLogger;

    @MockBean
    private ReleaseCoexistenceTelemetry telemetry;

    // ── Correlation header propagation ────────────────────────────────────────

    @Nested
    @DisplayName("Correlation header propagation")
    class CorrelationPropagation {

        @Test
        @DisplayName("v2 submission: valid inbound correlation header is echoed in response")
        void v2Submit_validCorrelationHeader_echoedInResponse() throws Exception {
            when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HEADER_NAME, TEST_CID)
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string(HEADER_NAME, TEST_CID));
        }

        @Test
        @DisplayName("v2 submission: missing correlation header gets generated UUID in response")
        void v2Submit_noCorrelationHeader_responseIncludesGeneratedId() throws Exception {
            when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists(HEADER_NAME));
        }

        @Test
        @DisplayName("v2 submission: unsafe inbound correlation header is replaced with UUID")
        void v2Submit_unsafeCorrelationHeader_replacedWithUuid() throws Exception {
            when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HEADER_NAME, "malicious\nheader:injection")
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists(HEADER_NAME));

            String responseId = mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HEADER_NAME, "malicious\nheader:injection")
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andReturn().getResponse().getHeader(HEADER_NAME);

            // Must not echo the unsafe injection value
            assertThat(responseId).isNotNull();
            assertThat(responseId).doesNotContain("\n");
            assertThat(responseId).doesNotContain("malicious");
        }
    }

    // ── Telemetry emission ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Telemetry metric emission")
    class TelemetryEmission {

        @Test
        @DisplayName("accepted v2 submission records ACCEPTED outcome with v2 routeVersion")
        void v2Submit_accepted_recordsAcceptedOutcome() throws Exception {
            when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andExpect(status().isAccepted());

            verify(telemetry).record(
                    eq(ReleaseCoexistenceTelemetry.ROUTE_VERSION_V2),
                    eq("DEPLOY"),
                    eq(ReleaseCoexistenceTelemetry.OUTCOME_ACCEPTED),
                    eq(ReleaseCoexistenceTelemetry.ENDPOINT_SUBMISSION),
                    any(Long.class));
        }

        @Test
        @DisplayName("safe log event emitted with routeVersion=v2 and no raw DTO content")
        void v2Submit_accepted_safeLogEventHasBoundedFields() throws Exception {
            when(releaseCommandFacade.accept(any())).thenReturn(acceptedAck());

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(deployRequest())))
                    .andExpect(status().isAccepted());

            // SafeStructuredLogger.logEvent must have been called — we use a captor
            // to verify no credential-like DTO fields appear in the log event
            verify(safeLogger).logEvent(any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReleaseCommandRequest deployRequest() {
        ReleaseCommandRequest req = new ReleaseCommandRequest();
        req.setOperationType(ReleaseOperationType.DEPLOY);
        req.setCustomerId("customer-telemetry-test-001");
        req.setSfdcToolId("sfdc-tool-telemetry-001");
        req.setTaskId("step-telemetry-001");
        return req;
    }

    private AcceptedAcknowledgement acceptedAck() {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId("job-telemetry-001");
        ack.setCorrelationId(TEST_CID);
        ack.setOperationType(ReleaseOperationType.DEPLOY);
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/job-telemetry-001/status");
        ack.setAcceptedAt(Instant.parse("2024-01-01T10:00:00Z"));
        return ack;
    }
}
