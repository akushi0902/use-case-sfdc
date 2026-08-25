package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.audit.AuditEventWriter;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.retention.RetentionMetadataService;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DataMigrationRequest;
import com.opsera.integrator.sfdc.service.DataDictionaryService;
import com.opsera.integrator.sfdc.service.DataMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization compatibility tests for the legacy data migration boundary in
 * {@link DataMigrationController}.
 *
 * <p><strong>Purpose:</strong> These tests capture the current HTTP contract for all
 * data migration and data dictionary routes so that future changes to shared validation,
 * logging, classification, or exception handling infrastructure cannot accidentally alter
 * the legacy response shape without a deliberate, reviewed migration step.
 *
 * <p><strong>Review gate:</strong> Any change that causes a test in this class to fail must
 * be accompanied by explicit migration guidance for downstream pipeline consumers.
 *
 * <p><strong>Fixture location:</strong> {@code src/test/resources/fixtures/data-migration/}.
 * Fixtures use only synthetic identifiers — no real customer data, Salesforce org identifiers,
 * credentials, or tokens.
 *
 * <p><strong>Runtime requirements:</strong> No Salesforce credentials, Kafka brokers, S3 storage,
 * database access, or shell execution is needed. All service dependencies are mocked.
 *
 * <p><strong>Contract inventory:</strong>
 * <ul>
 *   <li>LEGACY-DM-001: POST /datamigration — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-002: POST /customsettings — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-003: POST /list/fieldproperties — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-004: POST /validate/soql — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-005: POST /org/limits — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-006: POST /soql/details — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-007: POST /dependent-sobjects — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-008: POST /sobjects — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-009: POST /managedpackage-list — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-010: POST /list/filterableFields — plain SUCCESS acknowledgement</li>
 *   <li>LEGACY-DM-011: POST /validate/migration-entities — ACCEPTED checkpoint acknowledgement</li>
 *   <li>LEGACY-DM-012: POST /generate/data-dictionary — ACCEPTED checkpoint acknowledgement</li>
 * </ul>
 */
@WithMockUser
@WebMvcTest(controllers = DataMigrationController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("DataMigrationController — Legacy Compatibility")
class DataMigrationControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataMigrationService dataMigrationService;

    @MockBean
    private DataDictionaryService dataDictionaryService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    @MockBean
    private AuditEventWriter auditEventWriter;

    @MockBean
    private RetentionMetadataService retentionMetadataService;

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/data-migration/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private DataMigrationRequest minimalRequest(String seed) {
        return new DataMigrationRequest(
                "pipeline-fixture-" + seed,
                "step-fixture-" + seed,
                "https://source.fixture.example.internal",
                "https://target.fixture.example.internal");
    }

    // ── LEGACY-DM-001: POST /datamigration ──────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-001: POST /datamigration — governed migration initiation")
    class DataMigrationRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void migrate_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/datamigration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("dm-compat-001"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.migrate()")
        void migrate_validRequest_delegatesExactlyOnceToMigrateService() throws Exception {
            mockMvc.perform(post("/datamigration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("dm-compat-002"))))
                    .andExpect(status().isOk());

            ArgumentCaptor<DataMigrationRequest> captor =
                    ArgumentCaptor.forClass(DataMigrationRequest.class);
            verify(dataMigrationService, times(1)).migrate(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-dm-compat-002");
        }

        @Test
        @DisplayName("malformed JSON returns HTTP 400 with MALFORMED_REQUEST_BODY errorCode")
        void migrate_malformedJson_returns400WithMalformedBodyCode() throws Exception {
            mockMvc.perform(post("/datamigration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

            verify(dataMigrationService, never()).migrate(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 with INTERNAL_ERROR errorCode")
        void migrate_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            doThrow(new RuntimeException("upstream-failure")).when(dataMigrationService)
                    .migrate(any(DataMigrationRequest.class));

            mockMvc.perform(post("/datamigration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("dm-exc-001"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }
    }

    // ── LEGACY-DM-002: POST /customsettings ─────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-002: POST /customsettings — plain success acknowledgement")
    class CustomSettingsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void customSettings_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/customsettings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("custom-settings-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.listCustomSettings()")
        void customSettings_validRequest_delegatesToListCustomSettings() throws Exception {
            mockMvc.perform(post("/customsettings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("custom-settings-request.json")))
                    .andExpect(status().isOk());

            ArgumentCaptor<DataMigrationRequest> captor =
                    ArgumentCaptor.forClass(DataMigrationRequest.class);
            verify(dataMigrationService, times(1)).listCustomSettings(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-cs-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke DataDictionaryService for custom settings")
        void customSettings_validRequest_doesNotInvokeDataDictionaryService() throws Exception {
            mockMvc.perform(post("/customsettings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("custom-settings-request.json")))
                    .andExpect(status().isOk());

            verify(dataDictionaryService, never()).generateDataDictionary(any());
        }
    }

    // ── LEGACY-DM-003: POST /list/fieldproperties ────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-003: POST /list/fieldproperties — plain success acknowledgement")
    class FieldPropertiesRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void listFieldProperties_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/list/fieldproperties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("field-properties-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.listFieldProperties()")
        void listFieldProperties_validRequest_delegatesToListFieldProperties() throws Exception {
            mockMvc.perform(post("/list/fieldproperties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("field-properties-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).listFieldProperties(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-004: POST /validate/soql ──────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-004: POST /validate/soql — plain success acknowledgement")
    class SoqlValidationRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void validateSoql_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/validate/soql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("soql-validation-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.validateSoql()")
        void validateSoql_validRequest_delegatesToValidateSoql() throws Exception {
            mockMvc.perform(post("/validate/soql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("soql-validation-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).validateSoql(any(DataMigrationRequest.class));
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — does not expose upstream detail")
        void validateSoql_serviceThrows_returns500WithoutLeakingDetail() throws Exception {
            doThrow(new RuntimeException("soql-parse-failure")).when(dataMigrationService)
                    .validateSoql(any(DataMigrationRequest.class));

            mockMvc.perform(post("/validate/soql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("soql-validation-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── LEGACY-DM-005: POST /org/limits ──────────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-005: POST /org/limits — plain success acknowledgement")
    class OrgLimitsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void orgLimits_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/org/limits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("org-limits-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.getOrgLimits()")
        void orgLimits_validRequest_delegatesToGetOrgLimits() throws Exception {
            mockMvc.perform(post("/org/limits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("org-limits-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).getOrgLimits(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-006: POST /soql/details ────────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-006: POST /soql/details — plain success acknowledgement")
    class SoqlDetailsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void soqlDetails_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/soql/details")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("sd-compat-001"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.getSoqlDetails()")
        void soqlDetails_validRequest_delegatesToGetSoqlDetails() throws Exception {
            mockMvc.perform(post("/soql/details")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("sd-compat-002"))))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).getSoqlDetails(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-007: POST /dependent-sobjects ───────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-007: POST /dependent-sobjects — plain success acknowledgement")
    class DependentSObjectsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void dependentSObjects_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/dependent-sobjects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("dsobj-compat-001"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.getDependentSObjects()")
        void dependentSObjects_validRequest_delegatesToGetDependentSObjects() throws Exception {
            mockMvc.perform(post("/dependent-sobjects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("dsobj-compat-002"))))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).getDependentSObjects(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-008: POST /sobjects ─────────────────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-008: POST /sobjects — plain success acknowledgement")
    class SObjectsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void sObjects_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/sobjects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("sobjects-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.listSObjects()")
        void sObjects_validRequest_delegatesToListSObjects() throws Exception {
            mockMvc.perform(post("/sobjects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("sobjects-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).listSObjects(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-009: POST /managedpackage-list ──────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-009: POST /managedpackage-list — plain success acknowledgement")
    class ManagedPackageListRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void managedPackageList_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/managedpackage-list")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("managed-package-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.listManagedPackages()")
        void managedPackageList_validRequest_delegatesToListManagedPackages() throws Exception {
            mockMvc.perform(post("/managedpackage-list")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("managed-package-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).listManagedPackages(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-010: POST /list/filterableFields ────────────────────────────

    @Nested
    @DisplayName("LEGACY-DM-010: POST /list/filterableFields — plain success acknowledgement")
    class FilterableFieldsRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with plain SUCCESS body")
        void listFilterableFields_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/list/filterableFields")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("ff-compat-001"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.listFilterableFields()")
        void listFilterableFields_validRequest_delegatesToListFilterableFields() throws Exception {
            mockMvc.perform(post("/list/filterableFields")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(minimalRequest("ff-compat-002"))))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).listFilterableFields(any(DataMigrationRequest.class));
        }
    }

    // ── LEGACY-DM-011: POST /validate/migration-entities — checkpoint-style ───

    @Nested
    @DisplayName("LEGACY-DM-011: POST /validate/migration-entities — ACCEPTED checkpoint acknowledgement")
    class MigrationEntityValidationRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with ACCEPTED checkpoint body — not SUCCESS")
        void validateMigrationEntities_validRequest_returnsAcceptedBody() throws Exception {
            mockMvc.perform(post("/validate/migration-entities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("entity-validation-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ACCEPTED"));
        }

        @Test
        @DisplayName("acknowledgement body is ACCEPTED — distinct from synchronous SUCCESS acknowledgement")
        void validateMigrationEntities_responseBody_isAcceptedNotSuccess() throws Exception {
            var result = mockMvc.perform(post("/validate/migration-entities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("entity-validation-request.json")))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .isEqualTo("ACCEPTED")
                    .isNotEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataMigrationService.validateMigrationEntities()")
        void validateMigrationEntities_validRequest_delegatesToValidateMigrationEntities() throws Exception {
            mockMvc.perform(post("/validate/migration-entities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("entity-validation-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, times(1)).validateMigrationEntities(any(DataMigrationRequest.class));
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — does not expose migration entity context")
        void validateMigrationEntities_serviceThrows_returns500WithInternalErrorCode() throws Exception {
            doThrow(new RuntimeException("entity-check-failure")).when(dataMigrationService)
                    .validateMigrationEntities(any(DataMigrationRequest.class));

            mockMvc.perform(post("/validate/migration-entities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("entity-validation-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── LEGACY-DM-012: POST /generate/data-dictionary — checkpoint-style ──────

    @Nested
    @DisplayName("LEGACY-DM-012: POST /generate/data-dictionary — ACCEPTED checkpoint acknowledgement")
    class DataDictionaryRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with ACCEPTED checkpoint body — not SUCCESS")
        void generateDataDictionary_validRequest_returnsAcceptedBody() throws Exception {
            mockMvc.perform(post("/generate/data-dictionary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("dictionary-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ACCEPTED"));
        }

        @Test
        @DisplayName("valid request delegates exactly once to DataDictionaryService.generateDataDictionary()")
        void generateDataDictionary_validRequest_delegatesToDataDictionaryService() throws Exception {
            mockMvc.perform(post("/generate/data-dictionary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("dictionary-request.json")))
                    .andExpect(status().isOk());

            ArgumentCaptor<DataMigrationRequest> captor =
                    ArgumentCaptor.forClass(DataMigrationRequest.class);
            verify(dataDictionaryService, times(1)).generateDataDictionary(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-dict-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke DataMigrationService for dictionary generation")
        void generateDataDictionary_validRequest_doesNotInvokeDataMigrationService() throws Exception {
            mockMvc.perform(post("/generate/data-dictionary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("dictionary-request.json")))
                    .andExpect(status().isOk());

            verify(dataMigrationService, never()).migrate(any());
            verify(dataMigrationService, never()).listCustomSettings(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500 — does not expose dictionary content")
        void generateDataDictionary_serviceThrows_returns500WithoutLeakingContent() throws Exception {
            doThrow(new RuntimeException("dictionary-gen-failure")).when(dataDictionaryService)
                    .generateDataDictionary(any(DataMigrationRequest.class));

            mockMvc.perform(post("/generate/data-dictionary")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("dictionary-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }
}
