package com.opsera.integrator.sfdc.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SafeTraceAttributes} allow-list enforcement.
 */
class SafeTraceAttributesTest {

    @Test
    void validate_allowedKey_returnsUnchanged() {
        assertThat(SafeTraceAttributes.validate(SafeTraceAttributes.ATTR_OPERATION))
                .isEqualTo(SafeTraceAttributes.ATTR_OPERATION);
    }

    @Test
    void validate_allAllowedKeys_returnUnchanged() {
        for (String key : SafeTraceAttributes.ALLOWED_KEYS) {
            assertThat(SafeTraceAttributes.validate(key))
                    .as("Key '%s' should pass validation", key)
                    .isEqualTo(key);
        }
    }

    @Test
    void validate_disallowedKey_returnsRedactedSentinel() {
        assertThat(SafeTraceAttributes.validate("jobId"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
        assertThat(SafeTraceAttributes.validate("customerId"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
        assertThat(SafeTraceAttributes.validate("pipelineId"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
    }

    @Test
    void validate_nullKey_returnsRedactedSentinel() {
        assertThat(SafeTraceAttributes.validate(null))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
    }

    @Test
    void validate_emptyKey_returnsRedactedSentinel() {
        assertThat(SafeTraceAttributes.validate(""))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
    }

    @Test
    void validate_httpRouteKey_isAllowed() {
        assertThat(SafeTraceAttributes.validate(SafeTraceAttributes.ATTR_HTTP_ROUTE))
                .isEqualTo(SafeTraceAttributes.ATTR_HTTP_ROUTE);
    }

    @Test
    void validate_correlationIdKey_isAllowed() {
        assertThat(SafeTraceAttributes.validate(SafeTraceAttributes.ATTR_CORRELATION_ID))
                .isEqualTo(SafeTraceAttributes.ATTR_CORRELATION_ID);
    }

    @Test
    void validate_errorTypeKey_isAllowed() {
        assertThat(SafeTraceAttributes.validate(SafeTraceAttributes.ATTR_ERROR_TYPE))
                .isEqualTo(SafeTraceAttributes.ATTR_ERROR_TYPE);
    }

    @Test
    void validate_rawRequestBodyKey_isRedacted() {
        assertThat(SafeTraceAttributes.validate("requestBody"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
    }

    @Test
    void validate_credentialKey_isRedacted() {
        assertThat(SafeTraceAttributes.validate("bearerToken"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
        assertThat(SafeTraceAttributes.validate("sfdc.token"))
                .isEqualTo(SafeTraceAttributes.REDACTED_KEY);
    }

    @Test
    void allowedKeysSet_containsAllExpectedKeys() {
        assertThat(SafeTraceAttributes.ALLOWED_KEYS).containsExactlyInAnyOrder(
                SafeTraceAttributes.ATTR_OPERATION,
                SafeTraceAttributes.ATTR_LIFECYCLE_PHASE,
                SafeTraceAttributes.ATTR_OUTCOME,
                SafeTraceAttributes.ATTR_HTTP_ROUTE,
                SafeTraceAttributes.ATTR_CORRELATION_ID,
                SafeTraceAttributes.ATTR_ERROR_TYPE
        );
    }
}
