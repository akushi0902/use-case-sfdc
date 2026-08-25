package com.opsera.integrator.sfdc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding and validation of {@link SecretReferenceProperties} from application.yaml.
 * Activates {@code sfdc.secrets.*} property mappings and startup validation.
 */
@Configuration
@EnableConfigurationProperties(SecretReferenceProperties.class)
public class SecretConfig {
}
