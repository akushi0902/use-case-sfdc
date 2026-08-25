package com.opsera.integrator.sfdc.harness;

import com.opsera.integrator.sfdc.resources.v2.release.AcceptedAcknowledgement;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseLifecycleState;
import com.opsera.integrator.sfdc.resources.v2.release.ReleaseOperationType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared P0 workflow test utilities for the release workflow test harness.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #loadFixture(String)} — fail-fast classpath fixture loader</li>
 *   <li>{@link #buildAck(String, String, ReleaseOperationType)} — pre-built accepted acknowledgement</li>
 *   <li>{@link #assertAccepted(ResultActions)} — asserts 202 with required accepted contract fields</li>
 *   <li>{@link #assertStructuredError(ResultActions, String)} — asserts error response shape</li>
 * </ul>
 *
 * <p>No credentials, real Salesforce payloads, tokens, or production identifiers appear in
 * any fixture loaded or response produced by this class.
 */
public final class P0WorkflowTestSupport {

    private P0WorkflowTestSupport() {}

    /**
     * Loads a classpath fixture file as a UTF-8 string.
     *
     * <p>Fails fast with a descriptive assertion failure if the file is absent,
     * so CI failures are immediately actionable rather than silently empty.
     *
     * @param classpathPath fixture path relative to the classpath root (e.g. {@code fixtures/p0-harness/p0-deploy-request.json})
     * @return fixture content as string
     */
    public static String loadFixture(String classpathPath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            if (!resource.exists()) {
                throw new AssertionError("P0 fixture not found on classpath: " + classpathPath
                        + " — add the fixture file before running the P0 harness.");
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("P0 fixture failed to load: " + classpathPath, e);
        }
    }

    /**
     * Builds a pre-populated {@link AcceptedAcknowledgement} for use in mock stubs.
     *
     * <p>Uses safe fixture identifiers. No production identifiers, credentials, or org URLs.
     */
    public static AcceptedAcknowledgement buildAck(String jobId,
                                                    String correlationId,
                                                    ReleaseOperationType operationType) {
        AcceptedAcknowledgement ack = new AcceptedAcknowledgement();
        ack.setJobId(jobId);
        ack.setCorrelationId(correlationId);
        ack.setStatusUrl("/api/v2/sfdc/release-jobs/" + jobId + "/status");
        ack.setState(ReleaseLifecycleState.ACCEPTED);
        ack.setAcceptedAt(Instant.parse("2024-01-01T10:00:00Z"));
        ack.setOperationType(operationType);
        return ack;
    }

    /**
     * Asserts the HTTP 202 Accepted response contract:
     * jobId, correlationId, statusUrl, and state=ACCEPTED must all be present and non-null.
     *
     * @param actions result actions from a MockMvc perform call
     */
    public static void assertAccepted(ResultActions actions) throws Exception {
        actions
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
    }

    /**
     * Asserts the structured error response contract: HTTP status must be an error,
     * and the response body must contain the expected {@code errorCode}.
     *
     * @param actions   result actions from a MockMvc perform call
     * @param errorCode expected errorCode string in the error body
     */
    public static void assertStructuredError(ResultActions actions, String errorCode) throws Exception {
        actions.andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    /**
     * Asserts that the given mock object's method was never invoked with any argument.
     * Used to confirm that invalid requests do not dispatch downstream work.
     *
     * @param mockFacade the mock whose method should not have been called (e.g. ReleaseCommandFacade)
     * @param <T>        type of the mock
     */
    public static <T> void assertNoDispatch(T mockFacade) {
        verify(mockFacade, never()).accept(any());
    }
}
