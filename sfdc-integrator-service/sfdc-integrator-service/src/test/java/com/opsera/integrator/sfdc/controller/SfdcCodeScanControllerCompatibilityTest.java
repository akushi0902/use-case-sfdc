package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.codescan.QualityGateResult;
import com.opsera.integrator.sfdc.model.codescan.ScanCategory;
import com.opsera.integrator.sfdc.model.codescan.ScanRequest;
import com.opsera.integrator.sfdc.model.codescan.ScanRule;
import com.opsera.integrator.sfdc.model.codescan.ScanSummary;
import com.opsera.integrator.sfdc.services.codescan.SfdcCodeScanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization compatibility tests for the legacy code scan boundary in
 * {@link SfdcCodeScanController}.
 *
 * <p><strong>Purpose:</strong> These tests lock the observable HTTP contract for all
 * code scan and quality gate routes so that future changes to shared logging, exception
 * handling, or validation infrastructure cannot silently alter the response shapes that
 * release managers and quality operators depend on.
 *
 * <p><strong>Review gate:</strong> A failure here indicates an accidental change to a
 * locked legacy contract. Intentional changes require updated migration guidance.
 *
 * <p><strong>Fixture location:</strong> {@code src/test/resources/fixtures/code-scan/}.
 * All fixtures use synthetic identifiers — no proprietary source code, real repository
 * paths, customer scan findings, secrets, or credentials.
 *
 * <p><strong>Runtime requirements:</strong> No scanner scripts, Git commands, Salesforce
 * CLI, S3, database, or Kafka access. All scan service dependencies are mocked.
 *
 * <p><strong>Contract inventory:</strong>
 * <ul>
 *   <li>CS-001: GET  /code-scan/rules             — typed List&lt;ScanRule&gt;</li>
 *   <li>CS-002: GET  /code-scan/rules/by-category — typed List&lt;ScanRule&gt; filtered by category</li>
 *   <li>CS-003: GET  /code-scan/categories        — typed List&lt;ScanCategory&gt;</li>
 *   <li>CS-004: POST /code-scan/submit            — ACCEPTED checkpoint acknowledgement</li>
 *   <li>CS-005: GET  /code-scan/summary           — typed ScanSummary (found) or 404</li>
 *   <li>CS-006: GET  /code-scan/quality-gate      — typed QualityGateResult (found) or 404</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = SfdcCodeScanController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("SfdcCodeScanController — Legacy Code Scan Compatibility")
class SfdcCodeScanControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SfdcCodeScanService codeScanService;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/code-scan/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── CS-001: GET /code-scan/rules ─────────────────────────────────────────

    @Nested
    @DisplayName("CS-001: GET /code-scan/rules — typed rule list")
    class AllRulesRoute {

        @Test
        @DisplayName("rules available returns HTTP 200 with typed ScanRule list")
        void getAllRules_rulesPresent_returns200WithRuleList() throws Exception {
            List<ScanRule> rules = List.of(
                    new ScanRule("SFDC-APEX-001", "APEX_SECURITY", "CRITICAL",
                            "SOQL injection risk in dynamic query construction"),
                    new ScanRule("SFDC-VF-001", "VISUALFORCE_SECURITY", "HIGH",
                            "XSS risk via unescaped output in Visualforce component"));
            when(codeScanService.getAllRules()).thenReturn(rules);

            mockMvc.perform(get("/code-scan/rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].ruleId").value("SFDC-APEX-001"))
                    .andExpect(jsonPath("$[0].category").value("APEX_SECURITY"))
                    .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                    .andExpect(jsonPath("$[0].description").exists())
                    .andExpect(jsonPath("$[1].ruleId").value("SFDC-VF-001"));
        }

        @Test
        @DisplayName("empty rules list returns HTTP 200 with empty JSON array — not 404")
        void getAllRules_noRules_returns200WithEmptyList() throws Exception {
            when(codeScanService.getAllRules()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/rules"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("delegates exactly once to SfdcCodeScanService.getAllRules()")
        void getAllRules_delegatesExactlyOnce() throws Exception {
            when(codeScanService.getAllRules()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/rules")).andExpect(status().isOk());

            verify(codeScanService, times(1)).getAllRules();
        }

        @Test
        @DisplayName("response field names are stable: ruleId, category, severity, description")
        void getAllRules_responseFieldNamesAreStable() throws Exception {
            when(codeScanService.getAllRules()).thenReturn(List.of(
                    new ScanRule("SFDC-APEX-001", "APEX_SECURITY", "CRITICAL", "desc")));

            mockMvc.perform(get("/code-scan/rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].ruleId").exists())
                    .andExpect(jsonPath("$[0].category").exists())
                    .andExpect(jsonPath("$[0].severity").exists())
                    .andExpect(jsonPath("$[0].description").exists());
        }

        @Test
        @DisplayName("service exception returns HTTP 500 with INTERNAL_ERROR — no scan details leaked")
        void getAllRules_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            when(codeScanService.getAllRules()).thenThrow(new RuntimeException("rules-load-failure"));

            mockMvc.perform(get("/code-scan/rules"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── CS-002: GET /code-scan/rules/by-category ─────────────────────────────

    @Nested
    @DisplayName("CS-002: GET /code-scan/rules/by-category — filtered rule list")
    class RulesByCategoryRoute {

        @Test
        @DisplayName("category with rules returns HTTP 200 with filtered ScanRule list")
        void getRulesByCategory_categoryHasRules_returns200WithFilteredList() throws Exception {
            when(codeScanService.getRulesByCategory("APEX_SECURITY")).thenReturn(List.of(
                    new ScanRule("SFDC-APEX-001", "APEX_SECURITY", "CRITICAL", "SOQL injection risk"),
                    new ScanRule("SFDC-APEX-002", "APEX_SECURITY", "HIGH", "Hardcoded credential")));

            mockMvc.perform(get("/code-scan/rules/by-category")
                            .param("category", "APEX_SECURITY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].ruleId").value("SFDC-APEX-001"))
                    .andExpect(jsonPath("$[0].category").value("APEX_SECURITY"))
                    .andExpect(jsonPath("$[1].ruleId").value("SFDC-APEX-002"));
        }

        @Test
        @DisplayName("empty category returns HTTP 200 with empty JSON array — not 404")
        void getRulesByCategory_categoryEmpty_returns200WithEmptyList() throws Exception {
            when(codeScanService.getRulesByCategory(anyString())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/rules/by-category")
                            .param("category", "UNKNOWN_CATEGORY"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("delegates to SfdcCodeScanService.getRulesByCategory() with the requested category")
        void getRulesByCategory_delegatesWithCorrectCategory() throws Exception {
            when(codeScanService.getRulesByCategory("APEX_SECURITY")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/rules/by-category")
                            .param("category", "APEX_SECURITY"))
                    .andExpect(status().isOk());

            verify(codeScanService, times(1)).getRulesByCategory("APEX_SECURITY");
        }

        @Test
        @DisplayName("does NOT invoke getAllRules() when filtering by category")
        void getRulesByCategory_doesNotInvokeGetAllRules() throws Exception {
            when(codeScanService.getRulesByCategory(anyString())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/rules/by-category")
                            .param("category", "APEX_SECURITY"))
                    .andExpect(status().isOk());

            verify(codeScanService, never()).getAllRules();
        }
    }

    // ── CS-003: GET /code-scan/categories ────────────────────────────────────

    @Nested
    @DisplayName("CS-003: GET /code-scan/categories — typed category list")
    class CategoriesRoute {

        @Test
        @DisplayName("categories available returns HTTP 200 with typed ScanCategory list")
        void getCategories_categoriesPresent_returns200WithCategoryList() throws Exception {
            when(codeScanService.getCategories()).thenReturn(List.of(
                    new ScanCategory("APEX_SECURITY", "Apex Security", 2),
                    new ScanCategory("FLOW_QUALITY", "Flow Quality", 1)));

            mockMvc.perform(get("/code-scan/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].categoryId").value("APEX_SECURITY"))
                    .andExpect(jsonPath("$[0].name").value("Apex Security"))
                    .andExpect(jsonPath("$[0].ruleCount").value(2))
                    .andExpect(jsonPath("$[1].categoryId").value("FLOW_QUALITY"));
        }

        @Test
        @DisplayName("response field names are stable: categoryId, name, ruleCount")
        void getCategories_responseFieldNamesAreStable() throws Exception {
            when(codeScanService.getCategories()).thenReturn(List.of(
                    new ScanCategory("APEX_SECURITY", "Apex Security", 2)));

            mockMvc.perform(get("/code-scan/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].categoryId").exists())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].ruleCount").exists());
        }

        @Test
        @DisplayName("empty category list returns HTTP 200 with empty JSON array")
        void getCategories_noCategories_returns200WithEmptyList() throws Exception {
            when(codeScanService.getCategories()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/code-scan/categories"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    // ── CS-004: POST /code-scan/submit ────────────────────────────────────────

    @Nested
    @DisplayName("CS-004: POST /code-scan/submit — ACCEPTED checkpoint acknowledgement")
    class SubmitScanRoute {

        @Test
        @DisplayName("valid scan submission returns HTTP 200 with ACCEPTED body")
        void submitScan_validRequest_returns200WithAcceptedBody() throws Exception {
            mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scan-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ACCEPTED"));
        }

        @Test
        @DisplayName("acknowledgement is ACCEPTED — distinct from synchronous SUCCESS")
        void submitScan_responseBody_isAcceptedNotSuccess() throws Exception {
            var result = mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scan-request.json")))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .isEqualTo("ACCEPTED")
                    .isNotEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("delegates exactly once to SfdcCodeScanService.submitScan()")
        void submitScan_delegatesExactlyOnce() throws Exception {
            mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scan-request.json")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<ScanRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(ScanRequest.class);
            verify(codeScanService, times(1)).submitScan(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-cs-compat-001");
        }

        @Test
        @DisplayName("logs pipelineId and stepId only — repositoryUrl and branch are not logged")
        void submitScan_logsSelectedFieldsOnly() throws Exception {
            mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scan-request.json")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<SafeLogEvent> logCaptor =
                    org.mockito.ArgumentCaptor.forClass(SafeLogEvent.class);
            verify(safeStructuredLogger).logEvent(logCaptor.capture());

            SafeLogEvent event = logCaptor.getValue();
            assertThat(event.getOperation()).isEqualTo("code-scan-submit");
            assertThat(event.getSafeFields()).containsKey("pipelineId");
            assertThat(event.getSafeFields()).containsKey("stepId");
            assertThat(event.getSafeFields()).doesNotContainKey("repositoryUrl");
            assertThat(event.getSafeFields()).doesNotContainKey("branch");
        }

        @Test
        @DisplayName("malformed JSON returns HTTP 400 with MALFORMED_REQUEST_BODY — service not called")
        void submitScan_malformedJson_returns400WithMalformedBodyCode() throws Exception {
            mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

            verify(codeScanService, never()).submitScan(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — no repository path or scan detail leaked")
        void submitScan_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            doThrow(new RuntimeException("scan-submission-failure"))
                    .when(codeScanService).submitScan(any(ScanRequest.class));

            mockMvc.perform(post("/code-scan/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scan-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── CS-005: GET /code-scan/summary ────────────────────────────────────────

    @Nested
    @DisplayName("CS-005: GET /code-scan/summary — typed ScanSummary or 404 cache miss")
    class ScanSummaryRoute {

        private ScanSummary passSummary() {
            return new ScanSummary("scan-fixture-pass-001", "pipeline-fixture-cs-compat-001",
                    2, 0, 1, 1, 0, "PASS", "2024-01-15T10:00:00Z");
        }

        private ScanSummary failSummary() {
            return new ScanSummary("scan-fixture-fail-001", "pipeline-fixture-cs-compat-002",
                    8, 3, 4, 1, 0, "FAIL", "2024-01-15T11:00:00Z");
        }

        @Test
        @DisplayName("PASS scan summary found returns HTTP 200 with typed ScanSummary body")
        void getScanSummary_passResult_returns200WithPassStatus() throws Exception {
            when(codeScanService.getScanSummary("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passSummary()));

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").value("scan-fixture-pass-001"))
                    .andExpect(jsonPath("$.pipelineId").value("pipeline-fixture-cs-compat-001"))
                    .andExpect(jsonPath("$.status").value("PASS"))
                    .andExpect(jsonPath("$.criticalCount").value(0))
                    .andExpect(jsonPath("$.totalViolations").value(2));
        }

        @Test
        @DisplayName("FAIL scan summary found returns HTTP 200 with typed ScanSummary body showing FAIL status")
        void getScanSummary_failResult_returns200WithFailStatus() throws Exception {
            when(codeScanService.getScanSummary("pipeline-fixture-cs-compat-002"))
                    .thenReturn(Optional.of(failSummary()));

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-cs-compat-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAIL"))
                    .andExpect(jsonPath("$.criticalCount").value(3))
                    .andExpect(jsonPath("$.totalViolations").value(8));
        }

        @Test
        @DisplayName("response field names are stable: scanId, pipelineId, totalViolations, criticalCount, highCount, mediumCount, lowCount, status, scannedAt")
        void getScanSummary_responseFieldNamesAreStable() throws Exception {
            when(codeScanService.getScanSummary("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passSummary()));

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").exists())
                    .andExpect(jsonPath("$.pipelineId").exists())
                    .andExpect(jsonPath("$.totalViolations").exists())
                    .andExpect(jsonPath("$.criticalCount").exists())
                    .andExpect(jsonPath("$.highCount").exists())
                    .andExpect(jsonPath("$.mediumCount").exists())
                    .andExpect(jsonPath("$.lowCount").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.scannedAt").exists());
        }

        @Test
        @DisplayName("summary not found returns HTTP 404 — cache miss contract preserved")
        void getScanSummary_notFound_returns404() throws Exception {
            when(codeScanService.getScanSummary(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-missing"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("delegates to SfdcCodeScanService.getScanSummary() with correct pipelineId")
        void getScanSummary_delegatesWithCorrectPipelineId() throws Exception {
            when(codeScanService.getScanSummary("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passSummary()));

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk());

            verify(codeScanService, times(1)).getScanSummary("pipeline-fixture-cs-compat-001");
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — no scan content leaked")
        void getScanSummary_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            when(codeScanService.getScanSummary(anyString()))
                    .thenThrow(new RuntimeException("summary-lookup-failure"));

            mockMvc.perform(get("/code-scan/summary")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── CS-006: GET /code-scan/quality-gate ───────────────────────────────────

    @Nested
    @DisplayName("CS-006: GET /code-scan/quality-gate — typed QualityGateResult or 404")
    class QualityGateRoute {

        private QualityGateResult passResult() {
            return new QualityGateResult("pipeline-fixture-cs-compat-001",
                    "PASS", 0, 0, true, "2024-01-15T10:05:00Z");
        }

        private QualityGateResult failResult() {
            return new QualityGateResult("pipeline-fixture-cs-compat-002",
                    "FAIL", 0, 3, false, "2024-01-15T11:05:00Z");
        }

        @Test
        @DisplayName("PASS gate result found returns HTTP 200 with typed QualityGateResult body")
        void getQualityGateResult_passResult_returns200WithPassStatus() throws Exception {
            when(codeScanService.getQualityGateResult("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passResult()));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pipelineId").value("pipeline-fixture-cs-compat-001"))
                    .andExpect(jsonPath("$.status").value("PASS"))
                    .andExpect(jsonPath("$.passed").value(true))
                    .andExpect(jsonPath("$.criticalViolations").value(0));
        }

        @Test
        @DisplayName("FAIL gate result found returns HTTP 200 with typed QualityGateResult body showing FAIL")
        void getQualityGateResult_failResult_returns200WithFailStatus() throws Exception {
            when(codeScanService.getQualityGateResult("pipeline-fixture-cs-compat-002"))
                    .thenReturn(Optional.of(failResult()));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAIL"))
                    .andExpect(jsonPath("$.passed").value(false))
                    .andExpect(jsonPath("$.criticalViolations").value(3));
        }

        @Test
        @DisplayName("response field names are stable: pipelineId, status, criticalThreshold, criticalViolations, passed, evaluatedAt")
        void getQualityGateResult_responseFieldNamesAreStable() throws Exception {
            when(codeScanService.getQualityGateResult("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passResult()));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pipelineId").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.criticalThreshold").exists())
                    .andExpect(jsonPath("$.criticalViolations").exists())
                    .andExpect(jsonPath("$.passed").exists())
                    .andExpect(jsonPath("$.evaluatedAt").exists());
        }

        @Test
        @DisplayName("quality gate result not found returns HTTP 404 — cache miss contract preserved")
        void getQualityGateResult_notFound_returns404() throws Exception {
            when(codeScanService.getQualityGateResult(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-missing"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("delegates to SfdcCodeScanService.getQualityGateResult() with correct pipelineId")
        void getQualityGateResult_delegatesWithCorrectPipelineId() throws Exception {
            when(codeScanService.getQualityGateResult("pipeline-fixture-cs-compat-001"))
                    .thenReturn(Optional.of(passResult()));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk());

            verify(codeScanService, times(1)).getQualityGateResult("pipeline-fixture-cs-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke scan submission service for quality gate lookup")
        void getQualityGateResult_doesNotInvokeSubmitScan() throws Exception {
            when(codeScanService.getQualityGateResult(anyString()))
                    .thenReturn(Optional.of(passResult()));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isOk());

            verify(codeScanService, never()).submitScan(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — no quality gate threshold leaked")
        void getQualityGateResult_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            when(codeScanService.getQualityGateResult(anyString()))
                    .thenThrow(new RuntimeException("quality-gate-lookup-failure"));

            mockMvc.perform(get("/code-scan/quality-gate")
                            .param("pipelineId", "pipeline-fixture-cs-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }
}
