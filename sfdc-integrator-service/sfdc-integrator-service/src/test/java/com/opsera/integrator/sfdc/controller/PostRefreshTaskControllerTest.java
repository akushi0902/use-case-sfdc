package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
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
 * Controller tests for {@link PostRefreshTaskController} verifying legacy route compatibility
 * and that classification context is created at request entry (AC-2, AC-5).
 */
@WebMvcTest(controllers = PostRefreshTaskController.class)
@Import(SfdcExceptionHandler.class)
class PostRefreshTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostRefreshTaskService postRefreshTaskService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @Test
    void postRefresh_validRequest_returns200Success() throws Exception {
        PostRefreshRequest request = new PostRefreshRequest("pipeline-pr-001", "step-pr-001");

        mockMvc.perform(post("/postrefresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(postRefreshTaskService).execute(any(PostRefreshRequest.class));
    }

    @Test
    void postRefresh_validRequest_delegatesToPostRefreshTaskService() throws Exception {
        PostRefreshRequest request = new PostRefreshRequest("pipeline-pr-001", "step-pr-001");

        mockMvc.perform(post("/postrefresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(postRefreshTaskService).execute(any(PostRefreshRequest.class));
    }

    @Test
    void postRefresh_classificationContextResolvedAtEntry() throws Exception {
        ClassificationContext stubCtx = new ClassificationContext(
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategoryHint.STANDARD,
                "post-refresh");
        when(classificationPolicyResolver.resolve(any(), any())).thenReturn(stubCtx);

        PostRefreshRequest request = new PostRefreshRequest("pipeline-pr-003", "step-pr-003");

        mockMvc.perform(post("/postrefresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(classificationPolicyResolver).resolve(
                GovernanceDataCategory.JOB_METADATA, "post-refresh");
    }

    @Test
    void postRefresh_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/postrefresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }
}
