package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.governance.classification.ClassificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link ClassificationProperties} from application.yaml.
 * Activates {@code sfdc.governance.classification.*} property mappings.
 */
@Configuration
@EnableConfigurationProperties(ClassificationProperties.class)
public class GovernanceConfig {
}
