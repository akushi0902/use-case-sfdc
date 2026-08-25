package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.service.PostRefreshTaskService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for {@link PostRefreshTaskController} legacy post-refresh routes.
 *
 * <p>These tests lock down the current behavioral contract for Salesforce post-refresh task
 * operations so that later observability and instrumentation changes can prove compatibility.
 *
 * <p><b>Legacy compatibility contract (must remain stable):</b>
 * <ul>
 *   <li>POST /postrefresh → 200 "SUCCESS" after delegating to postRefreshTaskService.execute()</li>
 *   <li>POST /apex/schedulerclasses → 200 "SUCCESS" after delegating to retrieveSchedulerClasses()</li>
 *   <li>POST /domainqueue/task/remove → 200 "SUCCESS" after delegating to removeDomainQueueTask()</li>
 *   <li>GET /domainqueue/task/list → 200 JSON array from listDomainQueueTasks()</li>
 *   <li>POST /domainmap/clear → 200 "SUCCESS" after delegating to clearDomainMap()</li>
 * </ul>
 *
 * <p>Fixtures loaded from {@code fixtures/controllers/}. No real Salesforce, Kafka, or database.
 */
@WithMockUser
@WebMvcTest(controllers = PostRefreshTaskController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("Characterization — PostRefreshTaskController legacy post-refresh behavior")
class PostRefreshTaskControllerCharacterizationTest {

    private static final String POSTREFRESH_URL           = "/postrefresh";
    private static final String SCHEDULER_CLASSES_URL     = "/apex/schedulerclasses";
    private static final String DOMAIN_QUEUE_REMOVE_URL   = "/domainqueue/task/remove";
    private static final String DOMAIN_QUEUE_LIST_URL     = "/domainqueue/task/list";
    private static final String DOMAIN_MAP_CLEAR_URL      = "/domainmap/clear";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostRefreshTaskService postRefreshTaskService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeLogger;

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /postrefresh — execute post-refresh task
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /postrefresh — post-refresh task execution (CHAR-005)")
    class PostRefresh {

        @Test
        @DisplayName("valid request → 200 SUCCESS")
        void validRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/post-refresh-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(POSTREFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request → postRefreshTaskService.execute() called exactly once")
        void validRequest_serviceExecuteCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/post-refresh-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(POSTREFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService).execute(any());
        }

        @Test
        @DisplayName("malformed JSON → 400; service.execute() not called")
        void malformedJson_returns400_noDispatch() throws Exception {
            mockMvc.perform(post(POSTREFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{bad json"))
                    .andExpect(status().isBadRequest());

            verify(postRefreshTaskService, never()).execute(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /apex/schedulerclasses — Apex scheduler class retrieval
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /apex/schedulerclasses — Apex scheduler class retrieval (CHAR-005)")
    class SchedulerClasses {

        @Test
        @DisplayName("valid request → 200 SUCCESS")
        void validRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/post-refresh-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(SCHEDULER_CLASSES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request → postRefreshTaskService.retrieveSchedulerClasses() called once")
        void validRequest_serviceRetrieveCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/post-refresh-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(SCHEDULER_CLASSES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService).retrieveSchedulerClasses(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /domainqueue/task/remove — domain queue task removal
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /domainqueue/task/remove — domain queue removal (CHAR-006)")
    class DomainQueueRemove {

        @Test
        @DisplayName("valid request → 200 SUCCESS")
        void validRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/domain-queue-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DOMAIN_QUEUE_REMOVE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request → postRefreshTaskService.removeDomainQueueTask() called once")
        void validRequest_serviceRemoveCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/domain-queue-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DOMAIN_QUEUE_REMOVE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService).removeDomainQueueTask(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: GET /domainqueue/task/list — domain queue list
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /domainqueue/task/list — domain queue list (CHAR-006)")
    class DomainQueueList {

        @Test
        @DisplayName("list request → 200 with JSON array from service")
        void listRequest_returns200WithArray() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(anyString(), anyString()))
                    .thenReturn(List.of("char-task-001", "char-task-002"));

            mockMvc.perform(get(DOMAIN_QUEUE_LIST_URL)
                    .param("pipelineId", "char-pipeline-005")
                    .param("stepId", "char-step-005"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("char-task-001"))
                    .andExpect(jsonPath("$[1]").value("char-task-002"));
        }

        @Test
        @DisplayName("list request → service.listDomainQueueTasks() called with correct params")
        void listRequest_serviceCalled() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks("char-pipeline-005", "char-step-005"))
                    .thenReturn(List.of());

            mockMvc.perform(get(DOMAIN_QUEUE_LIST_URL)
                    .param("pipelineId", "char-pipeline-005")
                    .param("stepId", "char-step-005"))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService).listDomainQueueTasks("char-pipeline-005", "char-step-005");
        }

        @Test
        @DisplayName("empty list → 200 with empty array")
        void emptyList_returns200EmptyArray() throws Exception {
            when(postRefreshTaskService.listDomainQueueTasks(anyString(), anyString()))
                    .thenReturn(List.of());

            mockMvc.perform(get(DOMAIN_QUEUE_LIST_URL)
                    .param("pipelineId", "char-pipeline-005")
                    .param("stepId", "char-step-005"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-2: POST /domainmap/clear — domain map clear
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /domainmap/clear — domain map clear (CHAR-006)")
    class DomainMapClear {

        @Test
        @DisplayName("valid request → 200 SUCCESS")
        void validRequest_returns200Success() throws Exception {
            String body = loadFixture("fixtures/controllers/domain-queue-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DOMAIN_MAP_CLEAR_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("SUCCESS"));
        }

        @Test
        @DisplayName("valid request → postRefreshTaskService.clearDomainMap() called once")
        void validRequest_serviceClearCalled() throws Exception {
            String body = loadFixture("fixtures/controllers/domain-queue-request.json");
            body = stripCommentFields(body);

            mockMvc.perform(post(DOMAIN_MAP_CLEAR_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());

            verify(postRefreshTaskService).clearDomainMap(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static String loadFixture(String classpathPath) {
        try (InputStream is = PostRefreshTaskControllerCharacterizationTest.class
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
