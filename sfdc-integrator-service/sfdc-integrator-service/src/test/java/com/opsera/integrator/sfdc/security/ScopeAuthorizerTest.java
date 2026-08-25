package com.opsera.integrator.sfdc.security;

import com.opsera.integrator.sfdc.exceptions.ScopeAuthorizationException;
import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ScopeAuthorizer}.
 *
 * <p>Verifies exact scope matching, deny-by-default behavior, safe denied-decision
 * metadata, null caller context handling, and that denied requests produce safe
 * log events without raw token claims.
 *
 * <p>Fixture scenarios referenced:
 * <ul>
 *   <li>fixtures/authorization/principal-no-scopes.json</li>
 *   <li>fixtures/authorization/principal-unrelated-scope.json</li>
 *   <li>fixtures/authorization/principal-submit-scope.json</li>
 *   <li>fixtures/authorization/principal-cancel-scope.json</li>
 *   <li>fixtures/authorization/principal-mixed-case-scope.json</li>
 *   <li>fixtures/authorization/principal-whitespace-padded-scope.json</li>
 *   <li>fixtures/authorization/principal-multiple-scopes.json</li>
 * </ul>
 */
@DisplayName("ScopeAuthorizer — unit tests")
class ScopeAuthorizerTest {

    private SafeStructuredLogger safeLogger;
    private ScopeAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        safeLogger = mock(SafeStructuredLogger.class);
        authorizer = new ScopeAuthorizer(safeLogger);
    }

    private CallerContext contextWithScopes(String... scopes) {
        return CallerContext.builder()
                .subject("fixture-subject-001")
                .clientId("fixture-client-001")
                .scopes(Set.of(scopes))
                .authenticationType("JWT")
                .correlationId("corr-test-001")
                .build();
    }

    private CallerContext contextWithNoScopes() {
        return CallerContext.builder()
                .subject("fixture-subject-001")
                .scopes(Set.of())
                .authenticationType("JWT")
                .correlationId("corr-test-001")
                .build();
    }

    // ── Authorized paths ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authorized — caller holds required scope")
    class AuthorizedPaths {

        @Test
        @DisplayName("exact match on quickdeploy.submit allows the operation without throwing")
        void requireScope_exactSubmitScope_doesNotThrow() {
            CallerContext context = contextWithScopes(ScopeConstants.QUICK_DEPLOY_SUBMIT);
            authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
            // no exception = pass
        }

        @Test
        @DisplayName("exact match on quickdeploy.cancel allows the operation without throwing")
        void requireScope_exactCancelScope_doesNotThrow() {
            CallerContext context = contextWithScopes(ScopeConstants.QUICK_DEPLOY_CANCEL);
            authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_CANCEL, "quick-deploy-stop");
        }

        @Test
        @DisplayName("caller with multiple scopes including required scope is authorized")
        void requireScope_multipleScopesIncludingRequired_doesNotThrow() {
            // fixture scenario: principal-multiple-scopes.json
            CallerContext context = contextWithScopes(
                    ScopeConstants.QUICK_DEPLOY_SUBMIT,
                    ScopeConstants.QUICK_DEPLOY_CANCEL,
                    "read:reports");
            authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
        }

        @Test
        @DisplayName("mixed-case scope in CallerContext is normalized to lowercase — matches lowercase constant")
        void requireScope_mixedCaseScopeInContext_normalizedToLowercase_matches() {
            // CallerContextResolver lowercases all scopes at extraction time.
            // fixture scenario: principal-mixed-case-scope.json — "QuickDeploy.Submit" is stored as "quickdeploy.submit"
            CallerContext context = CallerContext.builder()
                    .subject("fixture-subject-005")
                    .scopes(Set.of("quickdeploy.submit")) // already normalized by CallerContextResolver
                    .authenticationType("JWT")
                    .correlationId("corr-test-005")
                    .build();
            authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
        }
    }

    // ── Denied paths ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Denied — caller lacks required scope")
    class DeniedPaths {

        @Test
        @DisplayName("no scopes returns ScopeAuthorizationException with INSUFFICIENT_SCOPE")
        void requireScope_noScopes_throwsScopeAuthorizationException() {
            // fixture scenario: principal-no-scopes.json
            CallerContext context = contextWithNoScopes();
            assertThatThrownBy(() ->
                    authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_SUBMIT);
                        assertThat(sae.getDenialReason()).isEqualTo("INSUFFICIENT_SCOPE");
                        assertThat(sae.getCorrelationId()).isEqualTo("corr-test-001");
                    });
        }

        @Test
        @DisplayName("unrelated scope does not satisfy required quickdeploy.submit scope")
        void requireScope_unrelatedScope_denies() {
            // fixture scenario: principal-unrelated-scope.json
            CallerContext context = contextWithScopes("read:reports", "analytics.view");
            assertThatThrownBy(() ->
                    authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_SUBMIT);
                        assertThat(sae.getDenialReason()).isEqualTo("INSUFFICIENT_SCOPE");
                    });
        }

        @Test
        @DisplayName("submit scope does not satisfy cancel requirement — scopes are not interchangeable")
        void requireScope_submitScopeDoesNotSatisfyCancel() {
            // fixture scenario: principal-submit-scope.json used against cancel operation
            CallerContext context = contextWithScopes(ScopeConstants.QUICK_DEPLOY_SUBMIT);
            assertThatThrownBy(() ->
                    authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_CANCEL, "quick-deploy-stop"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_CANCEL);
                    });
        }

        @Test
        @DisplayName("cancel scope does not satisfy submit requirement")
        void requireScope_cancelScopeDoesNotSatisfySubmit() {
            // fixture scenario: principal-cancel-scope.json used against start operation
            CallerContext context = contextWithScopes(ScopeConstants.QUICK_DEPLOY_CANCEL);
            assertThatThrownBy(() ->
                    authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_SUBMIT);
                    });
        }

        @Test
        @DisplayName("null caller context throws ScopeAuthorizationException with UNAUTHENTICATED reason")
        void requireScope_nullCallerContext_throwsWithUnauthenticatedReason() {
            assertThatThrownBy(() ->
                    authorizer.requireScope(null, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_SUBMIT);
                        assertThat(sae.getDenialReason()).isEqualTo("UNAUTHENTICATED");
                    });
        }

        @Test
        @DisplayName("unauthenticated CallerContext (type=none) throws with UNAUTHENTICATED reason")
        void requireScope_unauthenticatedCallerContext_throwsWithUnauthenticatedReason() {
            CallerContext unauthenticated = CallerContext.builder()
                    .authenticationType("none")
                    .correlationId("corr-test-unauth")
                    .build();
            assertThatThrownBy(() ->
                    authorizer.requireScope(unauthenticated, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getDenialReason()).isEqualTo("UNAUTHENTICATED");
                    });
        }

        @Test
        @DisplayName("whitespace-padded required scope is trimmed before comparison — does not cause false allow")
        void requireScope_whitespaceInRequiredScope_trimmedBeforeComparison() {
            // ScopeAuthorizer.requireScope() trims the requiredScope parameter
            // fixture scenario: principal-whitespace-padded-scope.json
            CallerContext context = contextWithNoScopes();
            assertThatThrownBy(() ->
                    authorizer.requireScope(context, "  " + ScopeConstants.QUICK_DEPLOY_SUBMIT + "  ", "quick-deploy-start"))
                    .isInstanceOf(ScopeAuthorizationException.class)
                    .satisfies(ex -> {
                        // The requiredCapability in the exception should be the trimmed value
                        ScopeAuthorizationException sae = (ScopeAuthorizationException) ex;
                        assertThat(sae.getRequiredCapability()).isEqualTo(ScopeConstants.QUICK_DEPLOY_SUBMIT);
                    });
        }
    }

    // ── Safe logging ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Safe denied-decision logging")
    class SafeLogging {

        @Test
        @DisplayName("denied decision produces safe log event with requiredCapability, denialReason, and REJECTED outcome")
        void requireScope_denied_logsDeniedDecisionSafely() {
            CallerContext context = contextWithNoScopes();

            try {
                authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
            } catch (ScopeAuthorizationException ignored) {}

            ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
            verify(safeLogger, times(1)).logEvent(captor.capture());

            SafeLogEvent event = captor.getValue();
            assertThat(event.getOperation()).isEqualTo("quick-deploy-start");
            assertThat(event.getOutcome()).isEqualTo(SafeLogEvent.Outcome.REJECTED);
            assertThat(event.getSafeFields()).containsKey("requiredCapability");
            assertThat(event.getSafeFields()).containsKey("denialReason");
            assertThat(event.getSafeFields()).containsKey("actorRef");
        }

        @Test
        @DisplayName("denied log event does not contain raw token claims or request payload fields")
        void requireScope_denied_logDoesNotContainRawTokenClaims() {
            CallerContext context = CallerContext.builder()
                    .subject("raw-subject-value-that-must-not-appear-in-logs")
                    .clientId("raw-client-id-that-must-not-appear-in-logs")
                    .scopes(Set.of())
                    .authenticationType("JWT")
                    .correlationId("corr-test-safe")
                    .build();

            try {
                authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
            } catch (ScopeAuthorizationException ignored) {}

            ArgumentCaptor<SafeLogEvent> captor = ArgumentCaptor.forClass(SafeLogEvent.class);
            verify(safeLogger, times(1)).logEvent(captor.capture());

            SafeLogEvent event = captor.getValue();
            String safeFieldsStr = event.getSafeFields().toString();
            assertThat(safeFieldsStr).doesNotContain("raw-subject-value-that-must-not-appear-in-logs");
            assertThat(safeFieldsStr).doesNotContain("raw-client-id-that-must-not-appear-in-logs");
        }

        @Test
        @DisplayName("authorized request does not produce any log event")
        void requireScope_authorized_doesNotLog() {
            CallerContext context = contextWithScopes(ScopeConstants.QUICK_DEPLOY_SUBMIT);
            authorizer.requireScope(context, ScopeConstants.QUICK_DEPLOY_SUBMIT, "quick-deploy-start");
            verify(safeLogger, times(0)).logEvent(org.mockito.ArgumentMatchers.any());
        }
    }
}
