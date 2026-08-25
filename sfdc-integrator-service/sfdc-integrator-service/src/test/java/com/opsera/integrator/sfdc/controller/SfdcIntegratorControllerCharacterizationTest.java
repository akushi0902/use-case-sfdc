package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.service.SfdcIntegratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for {@link SfdcIntegratorController} legacy deploy and validate routes.
 *
 * <p>These tests lock down the current behavioral contract for Salesforce metadata operations
 * so that later observability and instrumentation changes can prove compatibility.
 *
 * <p><b>Legacy compatibility contract (must remain stable):</b>
 * <ul>
 *   <li>POST /deploy with valid body → 200 "SUCCESS"</li>
 *   <li>POST /validate with valid body → 200 "SUCCESS"</li>
 *   <li>Both routes accept no-security-scope validation (legacy pattern, not @Valid-gated)</li>
 * </ul>
 *
 * <p>Fixtures loaded from {@code fixtures/controllers/}. No real Salesforce, Kafka, or database.
 */
@WithMockUser
@WebMvcTest(controllers = SfdcIntegratorController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("Characterization — SfdcIntegratorController legacy deploy and validate behavior")
class SfdcIntegratorControllerCharacterizationTest {

    private static final String DEPLOY_URL   = "/deploy";
    private static final String VALIDATE_URL = "/validate";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SfdcIntegratorService sfdcIntegratorService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeLogger;

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /deploy — deploy Salesforce metadata
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /deploy — Salesforce metadata deploy (CHAR-004)")
    class Deploy {

        @Test
        @DisplayName("valid deploy request → 200 SUCCESS")
        void validDeployRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid deploy request → sfdcIntegratorService.deploy() called exactly once")
        void validDeployRequest_serviceDelegateCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(sfdcIntegratorService).deploy(any());
        }

        @Test
        @DisplayName("empty body deploy → sfdcIntegratorService.deploy() still called (no @Valid on DeployRequest)")
        void emptyBodyDeploy_serviceCalledWithNullFields() throws Exception {
            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));

            verify(sfdcIntegratorService).deploy(any());
        }

        @Test
        @DisplayName("malformed JSON → 400 MALFORMED_REQUEST_BODY; service not called")
        void malformedJson_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(DEPLOY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{broken"))
                    .andExpect(status().isBadRequest());

            verify(sfdcIntegratorService, never()).deploy(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /validate — validate Salesforce metadata
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /validate — Salesforce metadata validate (CHAR-004)")
    class Validate {

        @Test
        @DisplayName("valid validate request → 200 SUCCESS")
        void validValidateRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid validate request → sfdcIntegratorService.validate() called exactly once")
        void validValidateRequest_serviceDelegateCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(sfdcIntegratorService).validate(any());
        }

        @Test
        @DisplayName("validate and deploy are distinct — deploy does not invoke validate and vice versa")
        void validateDoesNotCallDeploy() throws Exception {
            String body = loadFixture("fixtures/controllers/deploy-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(VALIDATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(sfdcIntegratorService).validate(any());
            verify(sfdcIntegratorService, never()).deploy(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static String loadFixture(String classpathPath) {
        try (InputStream is = SfdcIntegratorControllerCharacterizationTest.class
                .getClassLoader().getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new AssertionError("Fixture not found on classpath: " + classpathPath
                        + " — add it under src/test/resources/" + classpathPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to load fixture: " + classpathPath, e);
        }
    }

    private static String stripCommentFields(String json) {
        return json.replaceAll(",?\\s*\"_comment\"\\s*:\\s*\"[^\"]*\"", "")
                   .replaceAll("\\{\\s*,", "{");
    }
}
