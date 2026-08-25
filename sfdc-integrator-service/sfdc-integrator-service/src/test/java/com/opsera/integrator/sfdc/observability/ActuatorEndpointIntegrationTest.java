package com.opsera.integrator.sfdc.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that Actuator health and metrics endpoints are exposed
 * with the expected structure from {@code management.*} configuration.
 *
 * <p>Runs in the test profile which excludes Kafka and Hazelcast auto-configuration.
 * Health indicators designed for those components return UNKNOWN when the beans are absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Actuator Endpoint Integration Tests (WO-145)")
class ActuatorEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health returns 200 with status field")
    void actuatorHealth_returns200WithStatusField() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("GET /actuator/health includes components when show-details=always")
    void actuatorHealth_includesComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").exists());
    }

    @Test
    @DisplayName("GET /actuator/health includes jobDispatchReadiness component")
    void actuatorHealth_includesJobDispatchReadiness() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.jobDispatchReadiness").exists());
    }

    @Test
    @DisplayName("GET /actuator/health includes kafkaConnectivity component")
    void actuatorHealth_includesKafkaConnectivity() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.kafkaConnectivity").exists());
    }

    @Test
    @DisplayName("GET /actuator/health includes hazelcastCluster component")
    void actuatorHealth_includesHazelcastCluster() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.hazelcastCluster").exists());
    }

    @Test
    @DisplayName("GET /actuator/metrics returns 200")
    void actuatorMetrics_returns200() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/health/readiness returns 200")
    void actuatorHealthReadiness_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("GET /actuator/health/liveness returns 200")
    void actuatorHealthLiveness_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
