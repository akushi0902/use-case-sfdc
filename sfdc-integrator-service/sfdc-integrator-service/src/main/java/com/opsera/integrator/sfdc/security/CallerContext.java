package com.opsera.integrator.sfdc.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable value object holding the normalized caller identity extracted from an authenticated
 * JWT principal.
 *
 * <p>Fields are taken from standard JWT claims. Scope values are lowercased to prevent
 * case-based privilege escalation. Raw claim values are never logged or serialized
 * outside the security layer.
 *
 * <p>Obtain via {@link CallerContextResolver#resolve(org.springframework.security.core.Authentication)}.
 */
public final class CallerContext {

    private final String subject;
    private final String clientId;
    private final Set<String> scopes;
    private final String issuer;
    private final List<String> audience;
    private final String authenticationType;
    private final String correlationId;

    private CallerContext(Builder builder) {
        this.subject = builder.subject;
        this.clientId = builder.clientId;
        this.scopes = builder.scopes != null
                ? Collections.unmodifiableSet(new HashSet<>(builder.scopes))
                : Collections.emptySet();
        this.issuer = builder.issuer;
        this.audience = builder.audience != null
                ? Collections.unmodifiableList(builder.audience)
                : Collections.emptyList();
        this.authenticationType = builder.authenticationType;
        this.correlationId = builder.correlationId;
    }

    public String getSubject() { return subject; }

    public String getClientId() { return clientId; }

    public Set<String> getScopes() { return scopes; }

    public String getIssuer() { return issuer; }

    public List<String> getAudience() { return audience; }

    public String getAuthenticationType() { return authenticationType; }

    public String getCorrelationId() { return correlationId; }

    /** Returns true when the subject claim is present and non-blank. */
    public boolean hasSubject() {
        return subject != null && !subject.isBlank();
    }

    /** Returns true when the caller's scopes contain the requested scope (case-insensitive). */
    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope.toLowerCase());
    }

    /** Safe representation — never includes raw subject, clientId, or correlationId values. */
    @Override
    public String toString() {
        return "CallerContext{authenticationType=" + authenticationType
                + ", scopeCount=" + scopes.size()
                + ", hasSubject=" + hasSubject() + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String subject;
        private String clientId;
        private Set<String> scopes;
        private String issuer;
        private List<String> audience;
        private String authenticationType;
        private String correlationId;

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(List<String> audience) {
            this.audience = audience;
            return this;
        }

        public Builder authenticationType(String authenticationType) {
            this.authenticationType = authenticationType;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public CallerContext build() {
            return new CallerContext(this);
        }
    }
}
