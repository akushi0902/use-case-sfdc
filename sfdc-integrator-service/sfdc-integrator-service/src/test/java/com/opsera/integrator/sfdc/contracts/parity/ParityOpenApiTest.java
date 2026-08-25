package com.opsera.integrator.sfdc.contracts.parity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI parity tests asserting that routes and schemas used in parity test pairs
 * are documented in the generated specification (WO-153 AC-7).
 *
 * <p>Full Spring context with test profile — no credentials, mocked Kafka/Hazelcast/Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParityOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- V2 parity route is documented ----

    @Test
    void openApi_v2ReleaseJobsPath_isDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("/api/v2/sfdc/release-jobs");
    }

    // ---- Legacy parity routes are documented ----

    @Test
    void openApi_legacyQuickDeployPath_isDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("/quickdeploy");
    }

    // ---- Schemas used in parity pairs are documented ----

    @Test
    void openApi_releaseCommandRequestSchema_isDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("ReleaseCommandRequest");
    }

    @Test
    void openApi_acceptedAcknowledgementSchema_isDocumented() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("AcceptedAcknowledgement");
    }

    // ---- HTTP 202 response code is documented on the v2 submission route ----

    @Test
    void openApi_v2Route_documents202AcceptedResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("202");
    }

    // ---- No real credentials in spec ----

    @Test
    void openApi_spec_doesNotContainRealCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString().toLowerCase();
        assertThat(body)
                .doesNotContain("password=")
                .doesNotContain("bearer eyj")
                .doesNotContain("x-api-key: ")
                .doesNotContain("client_secret=");
    }
}
