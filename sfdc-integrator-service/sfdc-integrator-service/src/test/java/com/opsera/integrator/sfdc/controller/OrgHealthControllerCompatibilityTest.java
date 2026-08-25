package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.orghealth.MetadataCounts;
import com.opsera.integrator.sfdc.model.orghealth.MfaUserInfo;
import com.opsera.integrator.sfdc.model.orghealth.OrgHealthCollectRequest;
import com.opsera.integrator.sfdc.model.orghealth.OrgHealthResponse;
import com.opsera.integrator.sfdc.services.orghealth.MetadataCountService;
import com.opsera.integrator.sfdc.services.orghealth.OrgHealthCollectorService;
import com.opsera.integrator.sfdc.services.orghealth.SecurityComplianceCollector;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Characterization compatibility tests for the legacy org health boundary in
 * {@link OrgHealthController}.
 *
 * <p><strong>Purpose:</strong> These tests capture the current HTTP contract for all
 * org health, hygiene, and compliance routes so that shared infrastructure changes
 * (logging, classification, exception handling) cannot accidentally alter the contracts
 * that security and compliance operators depend on.
 *
 * <p><strong>Review gate:</strong> A failure here indicates an accidental change to a
 * locked legacy contract. Intentional changes require updated migration guidance.
 *
 * <p><strong>Fixture location:</strong> {@code src/test/resources/fixtures/org-health/}.
 * All fixtures use synthetic identifiers — no real user names, org IDs, customer data,
 * credentials, tokens, or tenant information.
 *
 * <p><strong>Runtime requirements:</strong> No Salesforce credentials, Kafka brokers,
 * S3 storage, database access, or shell execution. All collector dependencies are mocked.
 *
 * <p><strong>Contract inventory:</strong>
 * <ul>
 *   <li>OH-001: POST /org-health/collect — plain SUCCESS acknowledgement</li>
 *   <li>OH-002: GET  /org-health/info — typed OrgHealthResponse (found) or 404 (not found)</li>
 *   <li>OH-003: GET  /org-health/users-without-mfa — typed List&lt;MfaUserInfo&gt; (possibly empty)</li>
 *   <li>OH-004: GET  /org-health/metadata-counts — typed MetadataCounts (found) or 404 (not found)</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = OrgHealthController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("OrgHealthController — Legacy Org Health Compatibility")
class OrgHealthControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrgHealthCollectorService orgHealthCollectorService;

    @MockBean
    private SecurityComplianceCollector securityComplianceCollector;

    @MockBean
    private MetadataCountService metadataCountService;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/org-health/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── OH-001: POST /org-health/collect ─────────────────────────────────────

    @Nested
    @DisplayName("OH-001: POST /org-health/collect — plain SUCCESS acknowledgement")
    class CollectRoute {

        @Test
        @DisplayName("valid collect request returns HTTP 200 with plain SUCCESS body")
        void collect_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("collect-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid collect request delegates exactly once to OrgHealthCollectorService.collect()")
        void collect_validRequest_delegatesExactlyOnce() throws Exception {
            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("collect-request.json")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<OrgHealthCollectRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(OrgHealthCollectRequest.class);
            verify(orgHealthCollectorService, times(1)).collect(captor.capture());
            assertThat(captor.getValue().getCustomerId()).isEqualTo("cust-fixture-oh-compat-001");
            assertThat(captor.getValue().getToolId()).isEqualTo("tool-fixture-oh-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke SecurityComplianceCollector or MetadataCountService for collect")
        void collect_validRequest_doesNotInvokeOtherCollectors() throws Exception {
            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("collect-request.json")))
                    .andExpect(status().isOk());

            verify(securityComplianceCollector, never()).getUsersWithoutMfa(anyString(), anyString());
            verify(metadataCountService, never()).getMetadataCounts(anyString(), anyString());
        }

        @Test
        @DisplayName("logs customerId and toolId only — orgUrl and apiVersion are not logged")
        void collect_validRequest_logsSelectedFieldsOnly() throws Exception {
            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("collect-request.json")))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<SafeLogEvent> logCaptor =
                    org.mockito.ArgumentCaptor.forClass(SafeLogEvent.class);
            verify(safeStructuredLogger).logEvent(logCaptor.capture());

            SafeLogEvent event = logCaptor.getValue();
            assertThat(event.getOperation()).isEqualTo("org-health-collect");
            assertThat(event.getController()).isEqualTo("OrgHealthController");
            assertThat(event.getSafeFields()).containsKey("customerId");
            assertThat(event.getSafeFields()).containsKey("toolId");
            assertThat(event.getSafeFields()).doesNotContainKey("orgUrl");
            assertThat(event.getSafeFields()).doesNotContainKey("apiVersion");
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 with INTERNAL_ERROR errorCode")
        void collect_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            doThrow(new RuntimeException("collector-failure"))
                    .when(orgHealthCollectorService).collect(any(OrgHealthCollectRequest.class));

            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("collect-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }

        @Test
        @DisplayName("malformed JSON body returns HTTP 400 with MALFORMED_REQUEST_BODY — service not called")
        void collect_malformedJson_returns400WithMalformedBodyCode() throws Exception {
            mockMvc.perform(post("/org-health/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

            verify(orgHealthCollectorService, never()).collect(any());
        }
    }

    // ── OH-002: GET /org-health/info ─────────────────────────────────────────

    @Nested
    @DisplayName("OH-002: GET /org-health/info — typed OrgHealthResponse or 404 cache miss")
    class OrgHealthInfoRoute {

        private OrgHealthResponse stubResponse() {
            return new OrgHealthResponse(
                    "cust-fixture-oh-compat-001",
                    "tool-fixture-oh-compat-001",
                    "00Dfixture0000000001",
                    "58.0",
                    "HEALTHY",
                    85,
                    "2024-01-15T10:00:00Z");
        }

        @Test
        @DisplayName("cached report found returns HTTP 200 with typed OrgHealthResponse body")
        void getOrgHealthInfo_cacheHit_returns200WithTypedResponse() throws Exception {
            when(orgHealthCollectorService.getOrgHealthReport(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(Optional.of(stubResponse()));

            mockMvc.perform(get("/org-health/info")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value("cust-fixture-oh-compat-001"))
                    .andExpect(jsonPath("$.toolId").value("tool-fixture-oh-compat-001"))
                    .andExpect(jsonPath("$.orgId").value("00Dfixture0000000001"))
                    .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                    .andExpect(jsonPath("$.healthScore").value(85))
                    .andExpect(jsonPath("$.apiVersion").value("58.0"));
        }

        @Test
        @DisplayName("cached report not found returns HTTP 404 — current cache-miss contract preserved")
        void getOrgHealthInfo_cacheMiss_returns404() throws Exception {
            when(orgHealthCollectorService.getOrgHealthReport(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/org-health/info")
                            .param("customerId", "cust-fixture-missing")
                            .param("toolId", "tool-fixture-missing"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("delegates to OrgHealthCollectorService.getOrgHealthReport() with correct params")
        void getOrgHealthInfo_cacheHit_delegatesWithCorrectParams() throws Exception {
            when(orgHealthCollectorService.getOrgHealthReport(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(Optional.of(stubResponse()));

            mockMvc.perform(get("/org-health/info")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk());

            verify(orgHealthCollectorService, times(1))
                    .getOrgHealthReport("cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke SecurityComplianceCollector or MetadataCountService for info lookup")
        void getOrgHealthInfo_cacheHit_doesNotInvokeOtherCollectors() throws Exception {
            when(orgHealthCollectorService.getOrgHealthReport(anyString(), anyString()))
                    .thenReturn(Optional.of(stubResponse()));

            mockMvc.perform(get("/org-health/info")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk());

            verify(securityComplianceCollector, never()).getUsersWithoutMfa(anyString(), anyString());
            verify(metadataCountService, never()).getMetadataCounts(anyString(), anyString());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 with INTERNAL_ERROR errorCode")
        void getOrgHealthInfo_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            when(orgHealthCollectorService.getOrgHealthReport(anyString(), anyString()))
                    .thenThrow(new RuntimeException("cache-lookup-failure"));

            mockMvc.perform(get("/org-health/info")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── OH-003: GET /org-health/users-without-mfa ────────────────────────────

    @Nested
    @DisplayName("OH-003: GET /org-health/users-without-mfa — typed MfaUserInfo list or empty list")
    class UsersWithoutMfaRoute {

        @Test
        @DisplayName("users without MFA found returns HTTP 200 with typed user list")
        void getUsersWithoutMfa_usersFound_returns200WithUserList() throws Exception {
            List<MfaUserInfo> users = List.of(
                    new MfaUserInfo("user-fixture-001@fixture.example.internal", false),
                    new MfaUserInfo("user-fixture-002@fixture.example.internal", false));
            when(securityComplianceCollector.getUsersWithoutMfa(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(users);

            mockMvc.perform(get("/org-health/users-without-mfa")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").value("user-fixture-001@fixture.example.internal"))
                    .andExpect(jsonPath("$[0].mfaEnabled").value(false))
                    .andExpect(jsonPath("$[1].username").value("user-fixture-002@fixture.example.internal"));
        }

        @Test
        @DisplayName("empty MFA user list returns HTTP 200 with empty JSON array — not 404")
        void getUsersWithoutMfa_noUsersFound_returns200WithEmptyList() throws Exception {
            when(securityComplianceCollector.getUsersWithoutMfa(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/org-health/users-without-mfa")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("delegates to SecurityComplianceCollector.getUsersWithoutMfa() with correct params")
        void getUsersWithoutMfa_delegatesToSecurityComplianceCollector() throws Exception {
            when(securityComplianceCollector.getUsersWithoutMfa(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/org-health/users-without-mfa")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk());

            verify(securityComplianceCollector, times(1))
                    .getUsersWithoutMfa("cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke OrgHealthCollectorService or MetadataCountService for MFA lookup")
        void getUsersWithoutMfa_doesNotInvokeOtherCollectors() throws Exception {
            when(securityComplianceCollector.getUsersWithoutMfa(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/org-health/users-without-mfa")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk());

            verify(orgHealthCollectorService, never()).collect(any());
            verify(orgHealthCollectorService, never()).getOrgHealthReport(anyString(), anyString());
            verify(metadataCountService, never()).getMetadataCounts(anyString(), anyString());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — does not expose user data")
        void getUsersWithoutMfa_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            when(securityComplianceCollector.getUsersWithoutMfa(anyString(), anyString()))
                    .thenThrow(new RuntimeException("mfa-lookup-failure"));

            mockMvc.perform(get("/org-health/users-without-mfa")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── OH-004: GET /org-health/metadata-counts ───────────────────────────────

    @Nested
    @DisplayName("OH-004: GET /org-health/metadata-counts — typed MetadataCounts or 404 cache miss")
    class MetadataCountsRoute {

        private MetadataCounts stubCounts() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("ApexClass", 42);
            counts.put("ApexTrigger", 10);
            counts.put("CustomObject", 25);
            return new MetadataCounts(
                    "cust-fixture-oh-compat-001",
                    "tool-fixture-oh-compat-001",
                    counts);
        }

        @Test
        @DisplayName("cached counts found returns HTTP 200 with typed MetadataCounts body")
        void getMetadataCounts_cacheHit_returns200WithTypedResponse() throws Exception {
            when(metadataCountService.getMetadataCounts(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(Optional.of(stubCounts()));

            mockMvc.perform(get("/org-health/metadata-counts")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value("cust-fixture-oh-compat-001"))
                    .andExpect(jsonPath("$.toolId").value("tool-fixture-oh-compat-001"))
                    .andExpect(jsonPath("$.counts.ApexClass").value(42))
                    .andExpect(jsonPath("$.counts.ApexTrigger").value(10))
                    .andExpect(jsonPath("$.counts.CustomObject").value(25));
        }

        @Test
        @DisplayName("cached counts not found returns HTTP 404 — current cache-miss contract preserved")
        void getMetadataCounts_cacheMiss_returns404() throws Exception {
            when(metadataCountService.getMetadataCounts(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/org-health/metadata-counts")
                            .param("customerId", "cust-fixture-missing")
                            .param("toolId", "tool-fixture-missing"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("empty counts map returns HTTP 200 with stable JSON structure — empty counts object")
        void getMetadataCounts_emptyCounts_returns200WithEmptyCountsStructure() throws Exception {
            MetadataCounts emptyCounts = new MetadataCounts(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001",
                    Collections.emptyMap());
            when(metadataCountService.getMetadataCounts(anyString(), anyString()))
                    .thenReturn(Optional.of(emptyCounts));

            mockMvc.perform(get("/org-health/metadata-counts")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.counts").isMap())
                    .andExpect(jsonPath("$.customerId").value("cust-fixture-oh-compat-001"));
        }

        @Test
        @DisplayName("delegates to MetadataCountService.getMetadataCounts() with correct params")
        void getMetadataCounts_delegatesToMetadataCountService() throws Exception {
            when(metadataCountService.getMetadataCounts(
                    "cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001"))
                    .thenReturn(Optional.of(stubCounts()));

            mockMvc.perform(get("/org-health/metadata-counts")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isOk());

            verify(metadataCountService, times(1))
                    .getMetadataCounts("cust-fixture-oh-compat-001", "tool-fixture-oh-compat-001");
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — does not expose metadata content")
        void getMetadataCounts_serviceThrows_returns500WithoutLeakingContent() throws Exception {
            when(metadataCountService.getMetadataCounts(anyString(), anyString()))
                    .thenThrow(new RuntimeException("metadata-count-failure"));

            mockMvc.perform(get("/org-health/metadata-counts")
                            .param("customerId", "cust-fixture-oh-compat-001")
                            .param("toolId", "tool-fixture-oh-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }
}
