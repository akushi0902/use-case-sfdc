package com.opsera.integrator.sfdc.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the generated OpenAPI contract document.
 *
 * <p>Starts a full Spring context using the test profile (security disabled, H2 datasource,
 * Flyway disabled), retrieves the generated OpenAPI JSON from {@code /api-docs}, and asserts
 * that the v2 release contract is correctly documented.
 *
 * <h3>What is verified</h3>
 * <ul>
 *   <li>The documentation endpoint returns HTTP 200 with JSON content (AC-2)</li>
 *   <li>The v2 business route {@code /api/v2/sfdc/release-jobs} is present (AC-4)</li>
 *   <li>Key v2 schema components are published (AC-2, AC-3):
 *       ReleaseCommandRequest, AcceptedAcknowledgement</li>
 *   <li>HTTP 202 (accepted command), 400 (validation), 401 (unauthenticated),
 *       403 (insufficient scope), and 404 (disabled route) are documented (AC-6)</li>
 *   <li>The documentation path {@code /api-docs} is distinct from the business namespace
 *       {@code /api/v2/sfdc/} — neither contains the other (AC-4)</li>
 *   <li>No real credentials, bearer tokens, or Salesforce payloads appear in examples (AC-3)</li>
 * </ul>
 *
 * <h3>URL namespace note</h3>
 * <p>The path {@code /api-docs} is the Springdoc documentation endpoint (this test).
 * Business v2 routes are under {@code /api/v2/sfdc/}. These namespaces must remain distinct
 * so consumers cannot mistake the documentation path for a service contract version.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- Documentation endpoint accessibility ----

    @Test
    void apiDocs_endpoint_returns200WithJson() throws Exception {
        mockMvc.perform(get("/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ---- V2 business route is documented ----

    @Test
    void apiDocs_containsV2ReleaseJobsPath() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("/api/v2/sfdc/release-jobs"),
                "OpenAPI spec must document the v2 business route /api/v2/sfdc/release-jobs");
    }

    // ---- V2 schema components ----

    @Test
    void apiDocs_containsReleaseCommandRequestSchema() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("ReleaseCommandRequest"),
                "OpenAPI spec must include the ReleaseCommandRequest schema component");
    }

    @Test
    void apiDocs_containsAcceptedAcknowledgementSchema() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("AcceptedAcknowledgement"),
                "OpenAPI spec must include the AcceptedAcknowledgement schema component");
    }

    // ---- Required response codes are documented ----

    @Test
    void apiDocs_documents202AcceptedResponse() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("\"202\""),
                "OpenAPI spec must document HTTP 202 for accepted asynchronous commands");
    }

    @Test
    void apiDocs_documents400ValidationResponse() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("\"400\""),
                "OpenAPI spec must document HTTP 400 for validation failures");
    }

    @Test
    void apiDocs_documents401UnauthenticatedResponse() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("\"401\""),
                "OpenAPI spec must document HTTP 401 for unauthenticated requests");
    }

    @Test
    void apiDocs_documents403ForbiddenResponse() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("\"403\""),
                "OpenAPI spec must document HTTP 403 for insufficient scope");
    }

    @Test
    void apiDocs_documents404NotFoundResponse() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("\"404\""),
                "OpenAPI spec must document HTTP 404 for missing or disabled resources");
    }

    // ---- URL namespace disambiguation ----

    @Test
    void apiDocsPath_isDistinctFromBusinessV2Namespace() throws Exception {
        String body = getApiDocsBody();
        // The documentation path (/api-docs) must NOT appear inside the paths of the
        // business route namespace (/api/v2/sfdc/...). This assertion confirms the spec
        // does not confuse the two namespaces by nesting them.
        assertTrue(body.contains("/api/v2/sfdc/release-jobs"),
                "Business v2 routes must be at /api/v2/sfdc/, not under /api-docs");
        // No business route should be documented at the documentation path prefix
        assertTrue(!body.contains("\"/api-docs/api/v2"),
                "Business v2 routes must not be nested under the documentation path /api-docs");
    }

    // ---- Sanitized examples — no credentials ----

    @Test
    void apiDocs_examplesDoNotContainBearerTokenOrSecret() throws Exception {
        String body = getApiDocsBody();
        assertNotContainsSensitiveExampleValues(body);
    }

    // ---- Legacy routes are documented for coexistence ----

    @Test
    void apiDocs_containsLegacyQuickDeployPath() throws Exception {
        String body = getApiDocsBody();
        assertTrue(body.contains("/quickdeploy"),
                "OpenAPI spec must include legacy /quickdeploy route for coexistence documentation");
    }

    // ---- Helpers ----

    private String getApiDocsBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private void assertNotContainsSensitiveExampleValues(String spec) {
        String[] sensitivePatterns = {
                "Bearer eyJ",      // JWT token value
                "client_secret",   // OAuth client secret key
                "password=",       // URL-form credential
                "api_key=",        // API key parameter
                "SALESFORCE_TOKEN" // environment variable with real token
        };
        for (String pattern : sensitivePatterns) {
            assertTrue(!spec.toLowerCase().contains(pattern.toLowerCase()),
                    "OpenAPI spec must not contain sensitive example value: " + pattern);
        }
    }
}
