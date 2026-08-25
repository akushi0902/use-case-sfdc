package com.opsera.integrator.sfdc.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for JWT bearer token authentication.
 *
 * <p>Protected application paths require an authenticated JWT principal.
 * Actuator health and info endpoints are explicitly permitted for infrastructure probes.
 * Authentication failures return HTTP 401 with a structured error envelope.
 *
 * <p>The security filter chain is only registered when {@code sfdc.security.enabled=true}
 * (the default). Set to {@code false} in test profiles to prevent context startup failures
 * in Spring integration tests that do not exercise the security layer.
 *
 * <p>Production configuration requires one of:
 * <ul>
 *   <li>{@code OAUTH2_JWK_SET_URI} — JWK Set endpoint (no startup HTTP call; recommended)</li>
 *   <li>{@code OAUTH2_ISSUER_URI} — OIDC issuer URI (triggers discovery at startup)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "sfdc.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Registers the JWT resource server security filter chain.
     *
     * <p>All paths require authentication except {@code /actuator/health} and
     * {@code /actuator/info}, which are always permitted for infrastructure probes.
     * CSRF is disabled for the stateless API; sessions are never created.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(new SecurityAuthenticationEntryPoint())
            );
        return http.build();
    }

    /**
     * Provides a {@link JwtDecoder} with audience validation.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>JWK Set URI ({@code OAUTH2_JWK_SET_URI}) — lazy key fetch, no startup HTTP call.</li>
     *   <li>Issuer URI ({@code OAUTH2_ISSUER_URI}) — OIDC discovery at startup.</li>
     *   <li>Fail-closed fallback — rejects all tokens when neither is configured.</li>
     * </ol>
     *
     * <p>Annotated {@code @ConditionalOnMissingBean} so test configurations can provide a
     * stub {@link JwtDecoder} without triggering live OIDC calls.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${sfdc.security.expected-audience:sfdc-integrator}") String expectedAudience) {

        if (!jwkSetUri.isBlank()) {
            log.info("Configuring JWT decoder with JWK Set URI");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(),
                    new JwtAudienceValidator(expectedAudience));
            decoder.setJwtValidator(validator);
            return decoder;
        }

        if (!issuerUri.isBlank()) {
            log.info("Configuring JWT decoder with issuer URI");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuerUri),
                    new JwtAudienceValidator(expectedAudience));
            decoder.setJwtValidator(validator);
            return decoder;
        }

        log.warn("No OAUTH2_JWK_SET_URI or OAUTH2_ISSUER_URI configured; "
                + "all authentication requests will be rejected until an IdP is configured");
        return token -> {
            throw new JwtException(
                    "No JWT decoder configured. Set OAUTH2_JWK_SET_URI or OAUTH2_ISSUER_URI.");
        };
    }
}
