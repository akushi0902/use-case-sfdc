package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.governance.classification.ClassificationProperties;
import com.opsera.integrator.sfdc.governance.retention.RetentionPolicyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Enables binding of {@link ClassificationProperties} and {@link RetentionPolicyProperties}
 * from application.yaml.
 * Provides the UTC {@link Clock} bean used by retention expiration calculations.
 */
@Configuration
@EnableConfigurationProperties({ClassificationProperties.class, RetentionPolicyProperties.class})
public class GovernanceConfig {

    /**
     * UTC system clock for retention expiration calculations.
     * Override in tests by providing a fixed clock via @TestConfiguration.
     */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
