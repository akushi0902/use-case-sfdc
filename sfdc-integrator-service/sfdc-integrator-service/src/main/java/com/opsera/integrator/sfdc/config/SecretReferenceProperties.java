package com.opsera.integrator.sfdc.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Managed secret reference inventory for the SFDC Integrator Service.
 *
 * <p>Bound from the {@code sfdc.secrets.*} prefix in application.yaml. Each field documents
 * a configuration input by classification, injection mechanism, and which pod types require it.
 *
 * <p>Secret inventory:
 * <pre>
 * ┌──────────────────────────────────┬───────────────────┬──────────────────────────────┬────────────┬───────────────────────────────────┐
 * │ Property / Env Var               │ Classification    │ Injection Mechanism          │ Required   │ Notes                             │
 * ├──────────────────────────────────┼───────────────────┼──────────────────────────────┼────────────┼───────────────────────────────────┤
 * │ DB_URL                           │ NON-SECRET URL    │ Kubernetes Secret (env)      │ API+Worker │ Validated by Spring datasource    │
 * │ DB_USER                          │ CONFIDENTIAL ref  │ Kubernetes Secret (env)      │ API+Worker │ Validated by Spring datasource    │
 * │ DB_PASSWORD                      │ RESTRICTED cred   │ Kubernetes Secret (env)      │ API+Worker │ Must never appear in logs         │
 * │ KAFKA_BOOTSTRAP_SERVERS          │ NON-SECRET URL    │ Kubernetes ConfigMap (env)   │ API+Worker │ kubernetes profile only           │
 * │ OPSERA_REPO_URL                  │ NON-SECRET URL    │ CI secret store              │ Build only │ Skipped if not set                │
 * │ OPSERA_REPO_USER                 │ CONFIDENTIAL ref  │ CI secret store              │ Build only │ Filtered from build output        │
 * │ OPSERA_REPO_TOKEN                │ RESTRICTED cred   │ CI secret store              │ Build only │ Must never appear in build logs   │
 * │ OAUTH2_JWK_SET_URI               │ NON-SECRET URL    │ Kubernetes ConfigMap (env)   │ API        │ JWKS endpoint of identity provider│
 * │ OAUTH2_ISSUER_URI                │ NON-SECRET URL    │ Kubernetes ConfigMap (env)   │ API (alt)  │ OIDC discovery alternative        │
 * │ OAUTH2_EXPECTED_AUDIENCE         │ CONFIDENTIAL meta │ Kubernetes ConfigMap (env)   │ API        │ JWT aud claim value               │
 * │ VAULT_BASE_URL                   │ NON-SECRET URL    │ Kubernetes ConfigMap (env)   │ Future     │ Vault integration placeholder     │
 * └──────────────────────────────────┴───────────────────┴──────────────────────────────┴────────────┴───────────────────────────────────┘
 * </pre>
 *
 * <p>Validation rules:
 * <ul>
 *   <li>{@link #oauth2ExpectedAudience} must not be blank — a blank audience claim bypasses the
 *       security model silently. Set {@code OAUTH2_EXPECTED_AUDIENCE} in the deployment environment.</li>
 *   <li>All string fields reject known unsafe sample placeholder patterns (see {@link UnsafePlaceholder}).
 *       Unsafe patterns come from {@code .env.example} fixture files and must never reach a live pod.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "sfdc.secrets")
@Validated
public class SecretReferenceProperties {

    /**
     * Vault service base URL for future managed secret integration.
     * Classification: NON-SECRET endpoint reference.
     * Injection: {@code VAULT_BASE_URL} environment variable (Kubernetes ConfigMap).
     * Required: Optional — validated if set.
     */
    @UnsafePlaceholder
    private String vaultBaseUrl = "";

    /**
     * Expected OAuth2 audience claim value for JWT token validation.
     * Classification: CONFIDENTIAL metadata (identifies this service's identity namespace).
     * Injection: {@code OAUTH2_EXPECTED_AUDIENCE} environment variable (Kubernetes ConfigMap).
     * Required: API pod — must not be blank. Default is {@code sfdc-integrator}.
     */
    @NotBlank(message = "sfdc.secrets.oauth2-expected-audience must not be blank. "
            + "Remediation: set OAUTH2_EXPECTED_AUDIENCE in the deployment environment. "
            + "The value identifies this service in the JWT audience claim.")
    @UnsafePlaceholder
    private String oauth2ExpectedAudience = "sfdc-integrator";

    public String getVaultBaseUrl() {
        return vaultBaseUrl;
    }

    public void setVaultBaseUrl(String vaultBaseUrl) {
        this.vaultBaseUrl = vaultBaseUrl;
    }

    public String getOauth2ExpectedAudience() {
        return oauth2ExpectedAudience;
    }

    public void setOauth2ExpectedAudience(String oauth2ExpectedAudience) {
        this.oauth2ExpectedAudience = oauth2ExpectedAudience;
    }
}
