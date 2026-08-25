package com.opsera.integrator.sfdc.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CallerContextResolver} covering claim extraction, scope normalization,
 * missing-claim handling, and caller context construction (AC-1, AC-3, AC-5).
 * No Spring context required — resolver instantiated directly.
 */
class CallerContextResolverTest {

    private CallerContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CallerContextResolver();
    }

    // --- Null / unauthenticated ---

    @Test
    void resolve_nullAuthentication_returnsNoneContext() {
        CallerContext ctx = resolver.resolve(null);
        assertEquals("none", ctx.getAuthenticationType());
        assertFalse(ctx.hasSubject());
        assertTrue(ctx.getScopes().isEmpty());
    }

    @Test
    void resolve_notAuthenticatedPrincipal_returnsNoneContext() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        CallerContext ctx = resolver.resolve(auth);
        assertEquals("none", ctx.getAuthenticationType());
    }

    // --- Valid JWT with full claims ---

    @Test
    void resolve_jwtWithAllClaims_extractsAllFields() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "user-subject-001",
                "client_id", "sfdc-client-001",
                "scope", "quick-deploy read",
                "iss", "https://idp.example.internal",
                "aud", List.of("sfdc-integrator"),
                "exp", Instant.now().plusSeconds(3600),
                "iat", Instant.now()
        ));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());

        CallerContext ctx = resolver.resolve(token);

        assertEquals("JWT", ctx.getAuthenticationType());
        assertEquals("user-subject-001", ctx.getSubject());
        assertEquals("sfdc-client-001", ctx.getClientId());
        assertTrue(ctx.hasSubject());
        assertTrue(ctx.getScopes().contains("quick-deploy"));
        assertTrue(ctx.getScopes().contains("read"));
        assertEquals("https://idp.example.internal", ctx.getIssuer());
        assertTrue(ctx.getAudience().contains("sfdc-integrator"));
    }

    // --- Scope normalization: space-delimited string ---

    @Test
    void resolveScopes_spaceSeparatedString_splitsAndLowercases() {
        Jwt jwt = buildJwt(Map.of("scope", "Quick-Deploy READ Write"));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.contains("quick-deploy"));
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
        assertEquals(3, scopes.size());
    }

    @Test
    void resolveScopes_mixedCaseScope_lowercasedToPreventEscalation() {
        Jwt jwt = buildJwt(Map.of("scope", "ADMIN"));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.contains("admin"));
        assertFalse(scopes.contains("ADMIN"), "Mixed-case scope must be normalized to lowercase");
    }

    // --- Scope normalization: scp array claim ---

    @Test
    void resolveScopes_scpArrayClaim_usedWhenScopeStringAbsent() {
        Jwt jwt = buildJwt(Map.of("scp", List.of("quick-deploy", "read")));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.contains("quick-deploy"));
        assertTrue(scopes.contains("read"));
    }

    // --- Scope normalization: scopes array claim (alternate name) ---

    @Test
    void resolveScopes_scopesArrayClaim_usedWhenOtherClaimsAbsent() {
        Jwt jwt = buildJwt(Map.of("scopes", List.of("deploy", "validate")));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.contains("deploy"));
        assertTrue(scopes.contains("validate"));
    }

    // --- Scope string takes priority over array claims ---

    @Test
    void resolveScopes_scopeStringTakesPriorityOverScp() {
        Jwt jwt = buildJwt(Map.of(
                "scope", "quick-deploy",
                "scp", List.of("OTHER-SCOPE")
        ));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.contains("quick-deploy"));
        assertFalse(scopes.contains("other-scope"), "scope string should take priority over scp array");
    }

    // --- Missing scope claim ---

    @Test
    void resolveScopes_noScopeClaims_returnsEmptySet() {
        Jwt jwt = buildJwt(Map.of("sub", "user-001"));
        Set<String> scopes = resolver.resolveScopes(jwt);
        assertTrue(scopes.isEmpty());
    }

    // --- Missing subject ---

    @Test
    void resolve_jwtWithoutSubject_returnsContextWithNullSubject() {
        Jwt jwt = buildJwt(Map.of("scope", "read"));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());

        CallerContext ctx = resolver.resolve(token);

        assertFalse(ctx.hasSubject());
        assertNull(ctx.getSubject());
        assertEquals("JWT", ctx.getAuthenticationType());
    }

    // --- clientId: client_id preferred over azp ---

    @Test
    void resolve_jwtWithClientIdAndAzp_prefersClientId() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "user-002",
                "client_id", "primary-client",
                "azp", "azp-client"
        ));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());
        CallerContext ctx = resolver.resolve(token);
        assertEquals("primary-client", ctx.getClientId());
    }

    @Test
    void resolve_jwtWithAzpAndNoClientId_fallsBackToAzp() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "user-003",
                "azp", "azp-fallback-client"
        ));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());
        CallerContext ctx = resolver.resolve(token);
        assertEquals("azp-fallback-client", ctx.getClientId());
    }

    // --- Safe toString: never exposes raw values ---

    @Test
    void callerContext_toString_neverIncludesSubjectOrClientId() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "sensitive-subject-value",
                "client_id", "sensitive-client-id"
        ));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());
        CallerContext ctx = resolver.resolve(token);

        String str = ctx.toString();
        assertFalse(str.contains("sensitive-subject-value"), "toString must not expose subject");
        assertFalse(str.contains("sensitive-client-id"), "toString must not expose clientId");
    }

    // --- hasScope is case-insensitive ---

    @Test
    void callerContext_hasScope_caseInsensitive() {
        Jwt jwt = buildJwt(Map.of("scope", "Quick-Deploy"));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());
        CallerContext ctx = resolver.resolve(token);
        assertTrue(ctx.hasScope("quick-deploy"));
        assertTrue(ctx.hasScope("QUICK-DEPLOY"));
    }

    // --- Audience ---

    @Test
    void resolve_jwtWithAudience_populatesAudienceList() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "user-004",
                "aud", List.of("sfdc-integrator", "other-service")
        ));
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, Collections.emptyList());
        CallerContext ctx = resolver.resolve(token);
        assertTrue(ctx.getAudience().contains("sfdc-integrator"));
    }

    // ---- helpers ----

    private Jwt buildJwt(Map<String, Object> claims) {
        Map<String, Object> headers = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> allClaims = new HashMap<>(claims);
        allClaims.putIfAbsent("exp", Instant.now().plusSeconds(3600));
        allClaims.putIfAbsent("iat", Instant.now());
        return new Jwt("test-token-value", Instant.now(), Instant.now().plusSeconds(3600),
                headers, allClaims);
    }
}
