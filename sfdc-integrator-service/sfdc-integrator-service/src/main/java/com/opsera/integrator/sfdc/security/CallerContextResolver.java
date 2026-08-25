package com.opsera.integrator.sfdc.security;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts a normalized {@link CallerContext} from a Spring Security {@link Authentication}.
 *
 * <p>Supports {@link JwtAuthenticationToken} principals forwarded from an internal gateway.
 * Scope values are lowercased to prevent case-based privilege escalation. Raw JWT claim
 * values are never logged.
 *
 * <p>Scope extraction order:
 * <ol>
 *   <li>{@code scope} — space-delimited string (RFC 6749 standard format)</li>
 *   <li>{@code scp} — string array (some IdP variants)</li>
 *   <li>{@code scopes} — string array (alternative claim name)</li>
 * </ol>
 */
@Component
public class CallerContextResolver {

    private static final Logger log = LoggerFactory.getLogger(CallerContextResolver.class);

    /**
     * Resolves a {@link CallerContext} from the provided authentication.
     *
     * <p>Returns a context with {@code authenticationType="none"} when the authentication is
     * null or not authenticated. Never throws — extraction failures produce a partial context
     * and a WARN log entry.
     */
    public CallerContext resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return CallerContext.builder()
                    .authenticationType("none")
                    .correlationId(currentCorrelationId())
                    .build();
        }

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return resolveFromJwt(jwtToken.getToken());
        }

        return CallerContext.builder()
                .authenticationType(authentication.getClass().getSimpleName())
                .correlationId(currentCorrelationId())
                .build();
    }

    private CallerContext resolveFromJwt(Jwt jwt) {
        String correlationId = currentCorrelationId();
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            log.warn("JWT has no subject claim; partial CallerContext created. correlationId={}", correlationId);
        }

        return CallerContext.builder()
                .subject(subject)
                .clientId(resolveClientId(jwt))
                .scopes(resolveScopes(jwt))
                .issuer(jwt.getIssuer() != null ? jwt.getIssuer().toString() : null)
                .audience(jwt.getAudience())
                .authenticationType("JWT")
                .correlationId(correlationId)
                .build();
    }

    private String resolveClientId(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId == null || clientId.isBlank()) {
            clientId = jwt.getClaimAsString("azp");
        }
        return clientId;
    }

    /**
     * Resolves scopes from the JWT, trying space-delimited string first, then array claims.
     * All scope values are lowercased to prevent accidental privilege escalation via
     * mixed-case scope values.
     */
    Set<String> resolveScopes(Jwt jwt) {
        // RFC 6749 standard: space-delimited scope string
        String scopeStr = jwt.getClaimAsString("scope");
        if (scopeStr != null && !scopeStr.isBlank()) {
            return Arrays.stream(scopeStr.split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
        }

        // Array claim formats used by some IdP implementations
        for (String claimName : List.of("scp", "scopes")) {
            List<String> scopeList = jwt.getClaimAsStringList(claimName);
            if (scopeList != null && !scopeList.isEmpty()) {
                return scopeList.stream()
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet());
            }
        }

        return Collections.emptySet();
    }

    private String currentCorrelationId() {
        String id = MDC.get(CorrelationIdConstants.MDC_KEY);
        return id != null ? id : "unknown";
    }
}
