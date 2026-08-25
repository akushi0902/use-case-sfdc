package com.opsera.integrator.sfdc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.DomainQueueRequest;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
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
import java.util.Collections;
import java.util.List;

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
 * Characterization compatibility tests for legacy post-refresh routes in
 * {@link PostRefreshTaskController}.
 *
 * <p>Locks the HTTP contract for all 5 routes so that shared modernization work cannot
 * silently alter response shapes that Salesforce maintainers and pipeline automation depend on.
 *
 * <p>Contract inventory:
 * <ul>
 *   <li>PR-001: POST /postrefresh              — SUCCESS acknowledgement</li>
 *   <li>PR-002: POST /apex/schedulerclasses   — scheduler class retrieval SUCCESS</li>
 *   <li>PR-003: POST /domainqueue/task/remove — domain queue task removal SUCCESS</li>
 *   <li>PR-004: GET  /domainqueue/task/list   — domain queue task listing</li>
 *   <li>PR-005: POST /domainmap/clear         — domain map clear SUCCESS</li>
 * </ul>
 *
 * <p>All downstream post-refresh, domain queue, and Salesforce behaviors are mocked.
 * No real org access, queue mutation, or scheduler interaction occurs.
 */
@WithMockUser
@WebMvcTest(controllers = PostRefreshTaskController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("PostRefreshTaskController — Legacy Post-Refresh Compatibility")
class PostRefreshTaskControllerCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostRefreshTaskService postRefreshTaskService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/post-refresh/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── PR-001: POST /postrefresh ────────────────────────────────────────────

    @Nested
    @DisplayName("PR-001: POST /postrefresh — SUCCESS acknowledgement")
    class PostRefreshRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with SUCCESS body")
        void postRefresh_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/postrefresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("post-refresh-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("delegates exactly once to PostRefreshTaskService.execute()")
        void postRefresh_delegatesExactlyOnce() throws Exception {
            mockMvc.perform(post("/postrefresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("post-refresh-request.json")))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService, times(1)).execute(any(PostRefreshRequest.class));
        }

        @Test
        @DisplayName("malformed JSON returns HTTP 400 — service not called")
        void postRefresh_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/postrefresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalid }"))
                    .andExpect(status().isBadRequest());

            verify(postRefreshTaskService, never()).execute(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500")
        void postRefresh_serviceThrows_returns500() throws Exception {
            doThrow(new RuntimeException("post-refresh-failure"))
                    .when(postRefreshTaskService).execute(any(PostRefreshRequest.class));

            mockMvc.perform(post("/postrefresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("post-refresh-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }
    }

    // ── PR-002: POST /apex/schedulerclasses ──────────────────────────────────

    @Nested
    @DisplayName("PR-002: POST /apex/schedulerclasses — scheduler class retrieval SUCCESS")
    class SchedulerClassesRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with SUCCESS body")
        void schedulerClasses_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/apex/schedulerclasses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scheduler-classes-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("delegates to PostRefreshTaskService.retrieveSchedulerClasses()")
        void schedulerClasses_delegatesCorrectly() throws Exception {
            mockMvc.perform(post("/apex/schedulerclasses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scheduler-classes-request.json")))
                    .andExpect(status().isOk());

            ArgumentCaptor<PostRefreshRequest> captor =
                    ArgumentCaptor.forClass(PostRefreshRequest.class);
            verify(postRefreshTaskService, times(1)).retrieveSchedulerClasses(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-pr-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke postRefreshTaskService.execute() for scheduler route")
        void schedulerClasses_doesNotInvokeExecute() throws Exception {
            mockMvc.perform(post("/apex/schedulerclasses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scheduler-classes-request.json")))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService, never()).execute(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500")
        void schedulerClasses_serviceThrows_returns500() throws Exception {
            doThrow(new RuntimeException("scheduler-class-failure"))
                    .when(postRefreshTaskService).retrieveSchedulerClasses(any(PostRefreshRequest.class));

            mockMvc.perform(post("/apex/schedulerclasses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("scheduler-classes-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }

    // ── PR-003: POST /domainqueue/task/remove ────────────────────────────────

    @Nested
    @DisplayName("PR-003: POST /domainqueue/task/remove — domain queue task removal SUCCESS")
    class DomainQueueRemoveRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with SUCCESS body")
        void removeDomainQueueTask_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/domainqueue/task/remove")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("delegates to PostRefreshTaskService.removeDomainQueueTask()")
        void removeDomainQueueTask_delegatesCorrectly() throws Exception {
            mockMvc.perform(post("/domainqueue/task/remove")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isOk());

            ArgumentCaptor<DomainQueueRequest> captor =
                    ArgumentCaptor.forClass(DomainQueueRequest.class);
            verify(postRefreshTaskService, times(1)).removeDomainQueueTask(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-pr-compat-001");
            assertThat(captor.getValue().getDomainName()).isEqualTo("domain-fixture-compat-001");
        }

        @Test
        @DisplayName("malformed JSON returns HTTP 400 — service not called")
        void removeDomainQueueTask_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/domainqueue/task/remove")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ bad json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

            verify(postRefreshTaskService, never()).removeDomainQueueTask(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500")
        void removeDomainQueueTask_serviceThrows_returns500() throws Exception {
            doThrow(new RuntimeException("queue-remove-failure"))
                    .when(postRefreshTaskService).removeDomainQueueTask(any(DomainQueueRequest.class));

            mockMvc.perform(post("/domainqueue/task/remove")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }
    }

    // ── PR-004: GET /domainqueue/task/list ───────────────────────────────────

    @Nested
    @DisplayName("PR-004: GET /domainqueue/task/list — domain queue task listing")
    class DomainQueueListRoute {

        @Test
        @DisplayName("tasks present returns HTTP 200 with task list")
        void listDomainQueueTasks_tasksPresent_returns200WithList() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(
                    "pipeline-fixture-pr-compat-001", "step-fixture-queue-compat-001"))
                    .thenReturn(List.of("domain-fixture-compat-001", "domain-fixture-compat-002"));

            mockMvc.perform(get("/domainqueue/task/list")
                            .param("pipelineId", "pipeline-fixture-pr-compat-001")
                            .param("stepId", "step-fixture-queue-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("domain-fixture-compat-001"))
                    .andExpect(jsonPath("$[1]").value("domain-fixture-compat-002"));
        }

        @Test
        @DisplayName("empty queue returns HTTP 200 with empty JSON array — not 404")
        void listDomainQueueTasks_emptyQueue_returns200WithEmptyList() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/domainqueue/task/list")
                            .param("pipelineId", "pipeline-fixture-pr-compat-001")
                            .param("stepId", "step-fixture-queue-compat-001"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @DisplayName("delegates to PostRefreshTaskService.listDomainQueueTasks() with correct params")
        void listDomainQueueTasks_delegatesWithCorrectParams() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(
                    "pipeline-fixture-pr-compat-001", "step-fixture-queue-compat-001"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/domainqueue/task/list")
                            .param("pipelineId", "pipeline-fixture-pr-compat-001")
                            .param("stepId", "step-fixture-queue-compat-001"))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService, times(1))
                    .listDomainQueueTasks("pipeline-fixture-pr-compat-001", "step-fixture-queue-compat-001");
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500")
        void listDomainQueueTasks_serviceThrows_returns500() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(anyString(), anyString()))
                    .thenThrow(new RuntimeException("queue-list-failure"));

            mockMvc.perform(get("/domainqueue/task/list")
                            .param("pipelineId", "pipeline-fixture-pr-compat-001")
                            .param("stepId", "step-fixture-queue-compat-001"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
        }
    }

    // ── PR-005: POST /domainmap/clear ────────────────────────────────────────

    @Nested
    @DisplayName("PR-005: POST /domainmap/clear — domain map clear SUCCESS")
    class DomainMapClearRoute {

        @Test
        @DisplayName("valid request returns HTTP 200 with SUCCESS body")
        void clearDomainMap_validRequest_returns200WithSuccessBody() throws Exception {
            mockMvc.perform(post("/domainmap/clear")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("delegates to PostRefreshTaskService.clearDomainMap()")
        void clearDomainMap_delegatesCorrectly() throws Exception {
            mockMvc.perform(post("/domainmap/clear")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isOk());

            ArgumentCaptor<DomainQueueRequest> captor =
                    ArgumentCaptor.forClass(DomainQueueRequest.class);
            verify(postRefreshTaskService, times(1)).clearDomainMap(captor.capture());
            assertThat(captor.getValue().getPipelineId()).isEqualTo("pipeline-fixture-pr-compat-001");
        }

        @Test
        @DisplayName("does NOT invoke domain queue remove for domain map clear")
        void clearDomainMap_doesNotInvokeRemove() throws Exception {
            mockMvc.perform(post("/domainmap/clear")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService, never()).removeDomainQueueTask(any());
        }

        @Test
        @DisplayName("service exception propagates as HTTP 500")
        void clearDomainMap_serviceThrows_returns500() throws Exception {
            doThrow(new RuntimeException("domain-map-clear-failure"))
                    .when(postRefreshTaskService).clearDomainMap(any(DomainQueueRequest.class));

            mockMvc.perform(post("/domainmap/clear")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture("domain-queue-request.json")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal service error"));
        }
    }
}
