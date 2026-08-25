package com.opsera.integrator.sfdc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Returns HTTP 401 with a structured {@link ErrorResponse} when an unauthenticated
 * request reaches a protected path.
 *
 * <p>The response includes the active correlation identifier so operators can trace
 * authentication failures in logs. Raw exception messages are not forwarded to callers.
 */
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuthenticationEntryPoint.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (correlationId == null) {
            correlationId = "unknown";
        }

        log.warn("Authentication required: correlationId={}, uri={}", correlationId, request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                correlationId,
                "AUTHENTICATION_REQUIRED",
                "This endpoint requires an authenticated caller",
                "Provide a valid Bearer token in the Authorization header. "
                + "Obtain a token from the configured identity provider.");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
    }
}
