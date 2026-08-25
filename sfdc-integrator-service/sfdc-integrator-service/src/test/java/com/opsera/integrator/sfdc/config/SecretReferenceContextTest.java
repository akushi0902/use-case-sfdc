package com.opsera.integrator.sfdc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring application context tests for {@link SecretReferenceProperties} (AC-5).
 *
 * <p>Two complementary approaches:
 * <ol>
 *   <li>Full {@code @SpringBootTest} tests verify the context loads with the committed safe
 *       placeholder defaults.</li>
 *   <li>{@link ApplicationContextRunner} tests verify controlled failure modes without
 *       starting a full application server — faster and isolated.</li>
 * </ol>
 */
class SecretReferenceContextTest {

    // ---- ApplicationContextRunner for lightweight failure-mode tests ----

    @Configuration
    @EnableConfigurationProperties(SecretReferenceProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void contextLoads_withSafePlaceholderValues() {
        contextRunner
            .withPropertyValues(
                "sfdc.secrets.vault-base-url=https://vault.test.example.internal",
                "sfdc.secrets.oauth2-expected-audience=sfdc-integrator"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                SecretReferenceProperties props = context.getBean(SecretReferenceProperties.class);
                assertThat(props.getOauth2ExpectedAudience()).isEqualTo("sfdc-integrator");
                assertThat(props.getVaultBaseUrl()).isEqualTo("https://vault.test.example.internal");
            });
    }

    @Test
    void contextLoads_withDefaultValues_whenNoPropertiesSet() {
        contextRunner
            .run(context -> {
                assertThat(context).hasNotFailed();
                SecretReferenceProperties props = context.getBean(SecretReferenceProperties.class);
                // Default oauth2-expected-audience must not be blank
                assertThat(props.getOauth2ExpectedAudience()).isNotBlank();
                // Default vaultBaseUrl is empty (optional)
                assertThat(props.getVaultBaseUrl()).isEmpty();
            });
    }

    @Test
    void contextFails_whenOauth2ExpectedAudienceIsBlank() {
        contextRunner
            .withPropertyValues("sfdc.secrets.oauth2-expected-audience=")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("oauth2-expected-audience");
            });
    }

    @Test
    void contextFails_whenOauth2ExpectedAudienceIsWhitespaceOnly() {
        contextRunner
            .withPropertyValues("sfdc.secrets.oauth2-expected-audience=   ")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFails_whenVaultBaseUrlIsUnsafePlaceholder() {
        contextRunner
            .withPropertyValues("sfdc.secrets.vault-base-url=placeholder-vault-url-not-real")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("vault-base-url");
            });
    }

    @Test
    void contextFails_whenOauth2ExpectedAudienceIsUnsafePlaceholder() {
        contextRunner
            .withPropertyValues("sfdc.secrets.oauth2-expected-audience=placeholder-audience-not-real")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextLoads_withEmptyVaultBaseUrl_vaultIsOptional() {
        contextRunner
            .withPropertyValues(
                "sfdc.secrets.vault-base-url=",
                "sfdc.secrets.oauth2-expected-audience=sfdc-integrator"
            )
            .run(context -> assertThat(context).hasNotFailed());
    }

    // ---- Full SpringBootTest to verify integration with real application context ----

    /**
     * AC-5: context loads with committed placeholder secret references (safe defaults from
     * application.yaml plus test profile overrides).
     */
    @SpringBootTest
    @ActiveProfiles("test")
    static class FullContextLoads {

        @Autowired
        private SecretReferenceProperties secretProps;

        @Test
        void fullContext_secretPropertiesBound_withSafeDefaults() {
            assertNotNull(secretProps);
            assertFalse(secretProps.getOauth2ExpectedAudience().isBlank(),
                    "oauth2ExpectedAudience must not be blank in the test profile");
            assertFalse(secretProps.getOauth2ExpectedAudience().toLowerCase().contains("placeholder"),
                    "Default oauth2ExpectedAudience must not contain a placeholder pattern");
        }

        @Test
        void fullContext_vaultBaseUrl_defaultIsEmpty_notUnsafe() {
            assertNotNull(secretProps.getVaultBaseUrl());
            assertFalse(secretProps.getVaultBaseUrl().toLowerCase().contains("placeholder"),
                    "Default vaultBaseUrl must not contain a placeholder pattern");
        }
    }
}
