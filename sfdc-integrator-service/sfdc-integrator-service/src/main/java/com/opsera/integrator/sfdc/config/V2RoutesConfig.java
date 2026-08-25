package com.opsera.integrator.sfdc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link V2ReleaseRoutesProperties} from application.yaml.
 *
 * <p>V2 routes are enabled by default. To disable v2 submission for canary rollback,
 * set {@code sfdc.v2.release.routes.enabled=false}. Legacy routes are never affected.
 */
@Configuration
@EnableConfigurationProperties(V2ReleaseRoutesProperties.class)
public class V2RoutesConfig {
}
