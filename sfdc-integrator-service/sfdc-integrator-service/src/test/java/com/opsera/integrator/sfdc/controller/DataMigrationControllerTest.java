package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import com.opsera.integrator.sfdc.model.DataMigrationRequest;
import com.opsera.integrator.sfdc.service.DataMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link DataMigrationController} verifying legacy route compatibility
 * and that classification context is created at request entry (AC-2, AC-5).
 */
@WebMvcTest(controllers = DataMigrationController.class)
@Import(SfdcExceptionHandler.class)
class DataMigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataMigrationService dataMigrationService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    @Test
    void migrate_validRequest_returns200Success() throws Exception {
        DataMigrationRequest request = new DataMigrationRequest(
                "pipeline-dm-001", "step-dm-001",
                "https://source.example.internal", "https://target.example.internal");

        mockMvc.perform(post("/datamigration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(dataMigrationService).migrate(any(DataMigrationRequest.class));
    }

    @Test
    void migrate_validRequest_delegatesToDataMigrationService() throws Exception {
        DataMigrationRequest request = new DataMigrationRequest(
                "pipeline-dm-001", "step-dm-001",
                "https://source.example.internal", "https://target.example.internal");

        mockMvc.perform(post("/datamigration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(dataMigrationService).migrate(any(DataMigrationRequest.class));
    }

    @Test
    void migrate_classificationContextResolvedAtEntry() throws Exception {
        ClassificationContext stubCtx = new ClassificationContext(
                GovernanceDataCategory.SFDC_ARTIFACT,
                DataClassification.RESTRICTED,
                RetentionCategoryHint.EXTENDED,
                "data-migration");
        when(classificationPolicyResolver.resolve(any(), any())).thenReturn(stubCtx);

        DataMigrationRequest request = new DataMigrationRequest(
                "pipeline-dm-002", "step-dm-002", null, null);

        mockMvc.perform(post("/datamigration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(classificationPolicyResolver).resolve(
                GovernanceDataCategory.SFDC_ARTIFACT, "data-migration");
    }

    @Test
    void migrate_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/datamigration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json }"))
                .andExpect(status().isBadRequest());
    }
}
