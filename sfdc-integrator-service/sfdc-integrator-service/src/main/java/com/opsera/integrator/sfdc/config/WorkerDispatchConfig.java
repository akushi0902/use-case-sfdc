package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.command.WorkerDispatchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link WorkerDispatchProperties} from application.yaml.
 *
 * <p>Worker dispatch is disabled by default. To enable worker dispatch for specific command
 * types, set {@code sfdc.worker-dispatch.enabled=true} and configure per-command-type flags.
 */
@Configuration
@EnableConfigurationProperties(WorkerDispatchProperties.class)
public class WorkerDispatchConfig {
}
