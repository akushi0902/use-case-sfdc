package com.opsera.integrator.sfdc.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.HEADER_NAME;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.MAX_LENGTH;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.MDC_KEY;
import static com.opsera.integrator.sfdc.correlation.CorrelationIdConstants.SAFE_PATTERN;

/**
 * Servlet filter that installs a canonical correlation identifier for every HTTP request.
 *
 * <p>Behaviour per request:
 * <ol>
 *   <li>Reads the {@value CorrelationIdConstants#HEADER_NAME} request header.</li>
 *   <li>If present and safe, uses the caller-supplied value; otherwise generates a UUID.</li>
 *   <li>Writes the identifier to MDC under {@value CorrelationIdConstants#MDC_KEY}.</li>
 *   <li>Sets {@value CorrelationIdConstants#HEADER_NAME} on the response before the chain is
 *       invoked — so the header is present even when downstream processing throws.</li>
 *   <li>Clears the MDC entry in a {@code finally} block to prevent context leaking across
 *       thread-pooled servlet requests.</li>
 * </ol>
 *
 * <p>Unsafe inbound values (blank, control-character, non-ASCII, oversized) are silently
 * replaced and never copied to logs, error bodies, or downstream metadata.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(MDC_KEY, correlationId);
        // Set header before chain so it is present on both normal and error responses.
        response.setHeader(HEADER_NAME, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Returns a safe correlation identifier for the request.
     * Uses the caller-supplied header value when it is present and passes safety checks;
     * otherwise generates a new UUID without examining or logging the unsafe input.
     */
    static String resolveCorrelationId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER_NAME);
        return isSafe(inbound) ? inbound : UUID.randomUUID().toString();
    }

    /**
     * Returns {@code true} when {@code value} is a non-blank string within the maximum
     * permitted length that contains only characters matching {@link CorrelationIdConstants#SAFE_PATTERN}.
     */
    static boolean isSafe(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() > MAX_LENGTH) {
            return false;
        }
        return SAFE_PATTERN.matcher(value).matches();
    }
}
