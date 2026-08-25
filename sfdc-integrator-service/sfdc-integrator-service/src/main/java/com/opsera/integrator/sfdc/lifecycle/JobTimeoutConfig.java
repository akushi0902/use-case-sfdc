package com.opsera.integrator.sfdc.lifecycle;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling and binds {@link JobTimeoutProperties} from application.yaml.
 *
 * <p>Spring scheduling is activated here (not on the main application class)
 * so the scheduler is tied to the lifecycle monitor configuration rather than
 * being a global application-level concern.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobTimeoutProperties.class)
public class JobTimeoutConfig {
}
