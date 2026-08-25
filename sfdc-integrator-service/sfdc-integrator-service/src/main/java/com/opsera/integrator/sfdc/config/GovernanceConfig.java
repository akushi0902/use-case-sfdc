package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.governance.classification.ClassificationProperties;
import com.opsera.integrator.sfdc.governance.retention.RetentionPolicyProperties;
import com.opsera.integrator.sfdc.governance.retention.RetentionPurgeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Enables binding of governance {@link ClassificationProperties}, {@link RetentionPolicyProperties},
 * and {@link RetentionPurgeProperties} from application.yaml.
 * Provides the UTC {@link Clock} bean used by retention calculations.
 * Enables Spring scheduling for the retention purge scheduler.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ClassificationProperties.class, RetentionPolicyProperties.class,
        RetentionPurgeProperties.class})
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
