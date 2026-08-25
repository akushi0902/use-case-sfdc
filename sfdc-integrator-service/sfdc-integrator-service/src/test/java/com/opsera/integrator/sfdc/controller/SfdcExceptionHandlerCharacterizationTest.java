package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.exceptions.JobNotCancellableException;
import com.opsera.integrator.sfdc.exceptions.JobStatusNotFoundException;
import com.opsera.integrator.sfdc.exceptions.ScopeAuthorizationException;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.lifecycle.LifecyclePersistenceException;
import com.opsera.integrator.sfdc.security.ShellArgumentProfile;
import com.opsera.integrator.sfdc.security.ShellArgumentViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for {@link SfdcExceptionHandler} shared exception mappings.
 *
 * <p>These tests lock down the current HTTP status codes, error codes, and safe response shapes
 * for each handler method so that later observability changes can prove the handler is not
 * accidentally altered.
 *
 * <p><b>Handler contract (must remain stable):</b>
 * <ul>
 *   <li>MethodArgumentNotValidException → 400 VALIDATION_FAILED with fieldErrors</li>
 *   <li>HttpMessageNotReadableException (missing body) → 400 MISSING_REQUEST_BODY</li>
 *   <li>HttpMessageNotReadableException (malformed JSON) → 400 MALFORMED_REQUEST_BODY</li>
 *   <li>ShellArgumentViolationException → 400 SHELL_UNSAFE_INPUT</li>
 *   <li>ScopeAuthorizationException → 403 INSUFFICIENT_SCOPE</li>
 *   <li>JobStatusNotFoundException → 404 JOB_NOT_FOUND</li>
 *   <li>JobNotCancellableException → 409 JOB_NOT_CANCELLABLE</li>
 *   <li>LifecyclePersistenceException → 503 STATUS_SOURCE_UNAVAILABLE</li>
 *   <li>Exception (generic) → 500 INTERNAL_ERROR</li>
 * </ul>
 *
 * <p>A minimal trigger controller ({@link ExceptionTriggerController}) is loaded in the slice
 * to produce each exception on demand. No real services, databases, or Kafka are required.
 */
@WithMockUser
@WebMvcTest(controllers = SfdcExceptionHandlerCharacterizationTest.ExceptionTriggerController.class)
@Import(SfdcExceptionHandler.class)
@DisplayName("Characterization — SfdcExceptionHandler shared exception mappings")
class SfdcExceptionHandlerCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: Validation failure — MethodArgumentNotValidException
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("@Valid failure → 400 VALIDATION_FAILED")
    class ValidationFailed {

        @Test
        @DisplayName("missing required field → 400 VALIDATION_FAILED with fieldErrors")
        void missingRequiredField_returns400ValidationFailed() throws Exception {
            mockMvc.perform(post("/char-exception-test/validated")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @DisplayName("validation error response does not contain stack trace or raw value")
        void validationError_doesNotLeakStackTrace() throws Exception {
            mockMvc.perform(post("/char-exception-test/validated")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.exception").doesNotExist());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: Missing / malformed request body
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HttpMessageNotReadable → 400 MISSING/MALFORMED_REQUEST_BODY")
    class MalformedBody {

        @Test
        @DisplayName("missing request body → 400 MISSING_REQUEST_BODY")
        void missingBody_returns400MissingRequestBody() throws Exception {
            mockMvc.perform(post("/char-exception-test/validated")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MISSING_REQUEST_BODY"));
        }

        @Test
        @DisplayName("malformed JSON → 400 MALFORMED_REQUEST_BODY")
        void malformedJson_returns400MalformedRequestBody() throws Exception {
            mockMvc.perform(post("/char-exception-test/validated")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not-valid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: ShellArgumentViolationException → 400 SHELL_UNSAFE_INPUT
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ShellArgumentViolationException → 400 SHELL_UNSAFE_INPUT")
    class ShellUnsafeInput {

        @Test
        @DisplayName("shell violation → 400 SHELL_UNSAFE_INPUT with fieldErrors")
        void shellViolation_returns400ShellUnsafeInput() throws Exception {
            mockMvc.perform(get("/char-exception-test/shell-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("SHELL_UNSAFE_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @DisplayName("shell violation response does not contain raw rejected value")
        void shellViolation_doesNotLeakRawValue() throws Exception {
            mockMvc.perform(get("/char-exception-test/shell-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.rawValue").doesNotExist());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: ScopeAuthorizationException → 403 INSUFFICIENT_SCOPE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ScopeAuthorizationException → 403 INSUFFICIENT_SCOPE")
    class ScopeAuthorizationDenied {

        @Test
        @DisplayName("scope denied → 403 INSUFFICIENT_SCOPE")
        void scopeDenied_returns403InsufficientScope() throws Exception {
            mockMvc.perform(get("/char-exception-test/scope-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_SCOPE"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: JobStatusNotFoundException → 404 JOB_NOT_FOUND
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JobStatusNotFoundException → 404 JOB_NOT_FOUND")
    class JobNotFound {

        @Test
        @DisplayName("job not found → 404 JOB_NOT_FOUND")
        void jobNotFound_returns404JobNotFound() throws Exception {
            mockMvc.perform(get("/char-exception-test/job-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: JobNotCancellableException → 409 JOB_NOT_CANCELLABLE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JobNotCancellableException → 409 JOB_NOT_CANCELLABLE")
    class JobNotCancellable {

        @Test
        @DisplayName("terminal job cancellation → 409 JOB_NOT_CANCELLABLE")
        void terminalJob_returns409JobNotCancellable() throws Exception {
            mockMvc.perform(get("/char-exception-test/not-cancellable"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_CANCELLABLE"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: LifecyclePersistenceException → 503 STATUS_SOURCE_UNAVAILABLE
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LifecyclePersistenceException → 503 STATUS_SOURCE_UNAVAILABLE")
    class PersistenceUnavailable {

        @Test
        @DisplayName("lifecycle persistence failure → 503 STATUS_SOURCE_UNAVAILABLE")
        void lifecyclePersistenceFailure_returns503() throws Exception {
            mockMvc.perform(get("/char-exception-test/persistence-error"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("STATUS_SOURCE_UNAVAILABLE"));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AC-3: Generic unhandled exception → 500 INTERNAL_ERROR
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unhandled exception → 500 INTERNAL_ERROR")
    class GenericError {

        @Test
        @DisplayName("unhandled exception → 500 INTERNAL_ERROR (no stack trace in response)")
        void unhandledException_returns500InternalError() throws Exception {
            mockMvc.perform(get("/char-exception-test/generic-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Minimal trigger controller — used only within this test class
    // ════════════════════════════════════════════════════════════════════════

    /** Minimal request DTO with a required field for @Valid characterization tests. */
    static class ValidatedRequest {
        @NotBlank(message = "name is required")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Package-private trigger controller loaded only in this test slice.
     * Each route throws one of the exceptions mapped by {@link SfdcExceptionHandler}.
     */
    @RestController
    @RequestMapping("/char-exception-test")
    static class ExceptionTriggerController {

        @PostMapping("/validated")
        public String validated(@Valid @RequestBody ValidatedRequest req) {
            return "ok";
        }

        @GetMapping("/shell-violation")
        public String shellViolation() {
            throw new ShellArgumentViolationException(
                    "deploymentRequestId",
                    ShellArgumentProfile.DEPLOYMENT_IDENTIFIER,
                    "command-separator-or-metacharacter");
        }

        @GetMapping("/scope-denied")
        public String scopeDenied() {
            throw new ScopeAuthorizationException(
                    "QUICK_DEPLOY_SUBMIT",
                    "INSUFFICIENT_SCOPE",
                    "char-corr-001",
                    "svc-account-ref");
        }

        @GetMapping("/job-not-found")
        public String jobNotFound() {
            throw new JobStatusNotFoundException("char-job-001", "char-corr-002");
        }

        @GetMapping("/not-cancellable")
        public String notCancellable() {
            throw new JobNotCancellableException("char-job-002", "COMPLETED", "char-corr-003");
        }

        @GetMapping("/persistence-error")
        public String persistenceError() {
            throw new LifecyclePersistenceException("Simulated persistence failure");
        }

        @GetMapping("/generic-error")
        public String genericError() {
            throw new RuntimeException("Simulated unhandled error");
        }
    }
}
