package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.service.SfdcTestClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization compatibility tests for Apex test class discovery routes in
 * {@link SfdcTestClassController}.
 *
 * <p>Locks the HTTP contract for Apex test class listing and mapping routes so that
 * shared modernization work cannot silently alter the response shapes that pipeline
 * automation and quality operators depend on.
 *
 * <p>Contract inventory:
 * <ul>
 *   <li>TC-001: GET /apex/testclasses         — Apex test class list</li>
 *   <li>TC-002: GET /apex/testclasses/mapping — Apex test class mapping</li>
 * </ul>
 *
 * <p>No real Salesforce metadata, Apex discovery, or scheduler interaction occurs.
 * All downstream behavior is mocked.
 */
@WithMockUser
@WebMvcTest(controllers = SfdcTestClassController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("SfdcTestClassController — Legacy Apex Test Class Compatibility")
class SfdcTestClassControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SfdcTestClassService testClassService;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    // ── TC-001: GET /apex/testclasses ────────────────────────────────────────

    @Nested
    @DisplayName("TC-001: GET /apex/testclasses — Apex test class list")
    class TestClassListRoute {

        @Test
        @DisplayName("test classes available returns HTTP 200 with class list")
        void getTestClasses_classesPresent_returns200WithList() throws Exception {
            when(testClassService.getTestClasses(
                    "pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001"))
                    .thenReturn(List.of(
                            "FixtureApexTestClass001",
                            "FixtureApexTestClass002",
                            "FixtureApexTestClass003"));

            mockMvc.perform(get("/apex/testclasses")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("FixtureApexTestClass001"))
                    .andExpect(jsonPath("$[1]").value("FixtureApexTestClass002"))
                    .andExpect(jsonPath("$[2]").value("FixtureApexTestClass003"));
        }

        @Test
        @DisplayName("empty class list returns HTTP 200 with empty JSON array — not 404")
        void getTestClasses_noClasses_returns200WithEmptyList() throws Exception {
            when(testClassService.getTestClasses(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/apex/testclasses")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("delegates to SfdcTestClassService.getTestClasses() with correct params")
        void getTestClasses_delegatesWithCorrectParams() throws Exception {
            when(testClassService.getTestClasses(
                    "pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/apex/testclasses")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk());

            verify(testClassService, times(1))
                    .getTestClasses("pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke getTestClassMapping() when listing test classes")
        void getTestClasses_doesNotInvokeMapping() throws Exception {
            when(testClassService.getTestClasses(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/apex/testclasses")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk());

            verify(testClassService, never()).getTestClassMapping(anyString(), anyString());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — no Apex class details leaked")
        void getTestClasses_serviceThrows_returns500() throws Exception {
            when(testClassService.getTestClasses(anyString(), anyString()))
                    .thenThrow(new RuntimeException("test-class-discovery-failure"));

            mockMvc.perform(get("/apex/testclasses")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── TC-002: GET /apex/testclasses/mapping ────────────────────────────────

    @Nested
    @DisplayName("TC-002: GET /apex/testclasses/mapping — Apex test class mapping")
    class TestClassMappingRoute {

        @Test
        @DisplayName("mapping available returns HTTP 200 with mapping object")
        void getTestClassMapping_mappingPresent_returns200WithMapping() throws Exception {
            when(testClassService.getTestClassMapping(
                    "pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001"))
                    .thenReturn(Map.of(
                            "FixtureApexClass001", List.of("FixtureApexTestClass001", "FixtureApexTestClass002"),
                            "FixtureApexClass002", List.of("FixtureApexTestClass003")));

            mockMvc.perform(get("/apex/testclasses/mapping")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.FixtureApexClass001").isArray())
                    .andExpect(jsonPath("$.FixtureApexClass002").isArray());
        }

        @Test
        @DisplayName("empty mapping returns HTTP 200 with empty JSON object — not 404")
        void getTestClassMapping_emptyMapping_returns200WithEmptyObject() throws Exception {
            when(testClassService.getTestClassMapping(anyString(), anyString()))
                    .thenReturn(Collections.emptyMap());

            mockMvc.perform(get("/apex/testclasses/mapping")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{}"));
        }

        @Test
        @DisplayName("delegates to SfdcTestClassService.getTestClassMapping() with correct params")
        void getTestClassMapping_delegatesWithCorrectParams() throws Exception {
            when(testClassService.getTestClassMapping(
                    "pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001"))
                    .thenReturn(Collections.emptyMap());

            mockMvc.perform(get("/apex/testclasses/mapping")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk());

            verify(testClassService, times(1))
                    .getTestClassMapping("pipeline-fixture-tc-compat-001", "step-fixture-tc-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke getTestClasses() when requesting mapping")
        void getTestClassMapping_doesNotInvokeGetTestClasses() throws Exception {
            when(testClassService.getTestClassMapping(anyString(), anyString()))
                    .thenReturn(Collections.emptyMap());

            mockMvc.perform(get("/apex/testclasses/mapping")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isOk());

            verify(testClassService, never()).getTestClasses(anyString(), anyString());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — no mapping content leaked")
        void getTestClassMapping_serviceThrows_returns500() throws Exception {
            when(testClassService.getTestClassMapping(anyString(), anyString()))
                    .thenThrow(new RuntimeException("test-class-mapping-failure"));

            mockMvc.perform(get("/apex/testclasses/mapping")
                            .param("pipelineId", "pipeline-fixture-tc-compat-001")
                            .param("stepId", "step-fixture-tc-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }
}
