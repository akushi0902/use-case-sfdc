package com.opsera.integrator.sfdc.correlation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.HEADER_NAME;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC integration tests verifying that the {@link CorrelationIdFilter} is registered and
 * active for real HTTP request paths.
 *
 * <p>Tests target the {@code /actuator/health} endpoint (always available in this service) for
 * successful responses, and an unknown path for error responses, covering both normal and
 * exception-driven response paths per AC-1 through AC-4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- AC-2: Missing correlation header → generated UUID in response ----

    @Test
    void request_withoutCorrelationHeader_responseIncludesGeneratedCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        String responseCorrelation = result.getResponse().getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotNull().isNotBlank();
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    // ---- AC-1: Valid correlation header → same value echoed in response ----

    @Test
    void request_withValidCorrelationHeader_sameValueInResponse() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(HEADER_NAME, VALID_UUID))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER_NAME, VALID_UUID));
    }

    @Test
    void request_withValidShortCorrelationHeader_sameValueInResponse() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(HEADER_NAME, VALID_SHORT))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER_NAME, VALID_SHORT));
    }

    // ---- AC-3: Unsafe header → generated replacement in response, not the unsafe input ----

    @Test
    void request_withBlankCorrelationHeader_responseIncludesGeneratedId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header(HEADER_NAME, BLANK))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        String responseCorrelation = result.getResponse().getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotEqualTo(BLANK);
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    @Test
    void request_withOversizedCorrelationHeader_responseIncludesGeneratedId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header(HEADER_NAME, OVERSIZED))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        String responseCorrelation = result.getResponse().getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotEqualTo(OVERSIZED);
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    @Test
    void request_withNonAsciiCorrelationHeader_responseIncludesGeneratedId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header(HEADER_NAME, NON_ASCII))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        String responseCorrelation = result.getResponse().getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotEqualTo(NON_ASCII);
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    // ---- AC-4: Error response (404) still includes the correlation header ----

    @Test
    void request_toUnknownPath_errorResponseIncludesCorrelationHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/nonexistent-path")
                        .header(HEADER_NAME, VALID_UUID))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER_NAME)).isEqualTo(VALID_UUID);
    }

    @Test
    void request_toUnknownPath_withoutCorrelationHeader_stillIncludesGeneratedId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/nonexistent-path"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(HEADER_NAME))
                .andReturn();

        String responseCorrelation = result.getResponse().getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotNull().isNotBlank();
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    // ---- AC-1: Consecutive requests each get their own correlation identifier ----

    @Test
    void consecutiveRequests_eachGetSeparateCorrelationId() throws Exception {
        MvcResult first = mockMvc.perform(get("/actuator/health")).andReturn();
        MvcResult second = mockMvc.perform(get("/actuator/health")).andReturn();

        String firstId = first.getResponse().getHeader(HEADER_NAME);
        String secondId = second.getResponse().getHeader(HEADER_NAME);

        assertThat(firstId).isNotNull();
        assertThat(secondId).isNotNull();
        assertThat(firstId).isNotEqualTo(secondId);
    }
}
