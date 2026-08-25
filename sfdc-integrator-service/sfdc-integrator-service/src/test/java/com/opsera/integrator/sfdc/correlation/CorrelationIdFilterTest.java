package com.opsera.integrator.sfdc.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.stream.Stream;

import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.HEADER_NAME;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.MDC_KEY;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationIdFilter} covering validation, generation, MDC population,
 * response header assignment, and MDC cleanup.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    // ---- isSafe() validation tests ----

    @Test
    void isSafe_validUuid_returnsTrue() {
        assertThat(CorrelationIdFilter.isSafe(VALID_UUID)).isTrue();
    }

    @Test
    void isSafe_validShort_returnsTrue() {
        assertThat(CorrelationIdFilter.isSafe(VALID_SHORT)).isTrue();
    }

    @Test
    void isSafe_validMaxLength_returnsTrue() {
        assertThat(CorrelationIdFilter.isSafe(VALID_MAX_LENGTH)).isTrue();
        assertThat(VALID_MAX_LENGTH.length()).isEqualTo(64);
    }

    @Test
    void isSafe_singleChar_returnsTrue() {
        assertThat(CorrelationIdFilter.isSafe(VALID_SINGLE_CHAR)).isTrue();
    }

    @Test
    void isSafe_null_returnsFalse() {
        assertThat(CorrelationIdFilter.isSafe(MISSING)).isFalse();
    }

    @Test
    void isSafe_blank_returnsFalse() {
        assertThat(CorrelationIdFilter.isSafe(BLANK)).isFalse();
    }

    @Test
    void isSafe_empty_returnsFalse() {
        assertThat(CorrelationIdFilter.isSafe(EMPTY)).isFalse();
    }

    @Test
    void isSafe_oversized_returnsFalse() {
        assertThat(OVERSIZED.length()).isGreaterThan(64);
        assertThat(CorrelationIdFilter.isSafe(OVERSIZED)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("unsafeValues")
    void isSafe_unsafeValues_returnsFalse(String unsafe) {
        assertThat(CorrelationIdFilter.isSafe(unsafe)).isFalse();
    }

    static Stream<String> unsafeValues() {
        return Stream.of(
                BLANK, EMPTY, OVERSIZED, CONTROL_CHAR, NON_ASCII,
                NEWLINE_INJECTION, CARRIAGE_RETURN, SPECIAL_CHARS, CONTAINS_SPACE
        );
    }

    // ---- Filter lifecycle tests ----

    @Test
    void filter_validCorrelationId_propagatesUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_UUID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HEADER_NAME)).isEqualTo(VALID_UUID);
    }

    @Test
    void filter_missingCorrelationId_generatesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseCorrelation = response.getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotNull().isNotBlank();
        // Generated value must be UUID-parseable
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    @Test
    void filter_blankCorrelationId_generatesReplacement() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, BLANK);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseCorrelation = response.getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotNull().isNotEqualTo(BLANK);
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("unsafeValues")
    void filter_unsafeInboundValue_replacesWithUuid(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (unsafe != null) {
            request.addHeader(HEADER_NAME, unsafe);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseCorrelation = response.getHeader(HEADER_NAME);
        assertThat(responseCorrelation).isNotNull().isNotBlank();
        // Must not echo the unsafe input in the response
        if (unsafe != null) {
            assertThat(responseCorrelation).isNotEqualTo(unsafe);
        }
        // Generated value must be UUID-parseable
        assertThat(UUID.fromString(responseCorrelation)).isNotNull();
    }

    @Test
    void filter_populatesMdcDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_UUID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture MDC value from inside the chain
        String[] mdcCapture = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                mdcCapture[0] = MDC.get(MDC_KEY);
                super.doFilter(req, res);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(mdcCapture[0]).isEqualTo(VALID_UUID);
    }

    @Test
    void filter_clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_UUID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void filter_clearsMdcEvenWhenChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_UUID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throw new jakarta.servlet.ServletException("simulated downstream failure");
            }
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (jakarta.servlet.ServletException expected) {
            // expected
        }

        // MDC must be cleared regardless of downstream failure
        assertThat(MDC.get(MDC_KEY)).isNull();
        // Response header must still be set (set before chain.doFilter)
        assertThat(response.getHeader(HEADER_NAME)).isEqualTo(VALID_UUID);
    }

    @Test
    void filter_responseHeaderSetBeforeChain_presentOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throw new jakarta.servlet.ServletException("simulated failure");
            }
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (jakarta.servlet.ServletException expected) {
            // expected
        }

        assertThat(response.getHeader(HEADER_NAME)).isNotNull().isNotBlank();
    }
}
