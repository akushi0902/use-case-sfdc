package com.opsera.integrator.sfdc.observability.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for Kafka bootstrap connectivity.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>If bootstrap servers are not configured → {@code UNKNOWN} (not an error — Kafka is
 *       optional in local and test environments).</li>
 *   <li>If configured → {@code UP}, indicating that Kafka connectivity is expected to be
 *       available. A lightweight check is used: verifying configuration only to avoid
 *       blocking Kubernetes probes with a synchronous broker connection attempt.</li>
 * </ul>
 *
 * <p>Health detail values are bounded and safe — no credential values, endpoint URLs
 * with credentials, or raw configuration content are included.
 *
 * <p>The Spring Boot actuator strips the {@code HealthIndicator} suffix and lower-cases the
 * component name when registering health groups. This indicator is registered as
 * {@code kafkaConnectivity} and should be referenced with that name in health group configuration.
 */
@Component("kafkaConnectivityHealthIndicator")
public class KafkaConnectivityHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnectivityHealthIndicator.class);

    private final String bootstrapServers;

    public KafkaConnectivityHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        try {
            if (bootstrapServers == null || bootstrapServers.isBlank()) {
                return Health.unknown()
                        .withDetail("reason", "bootstrap-servers-not-configured")
                        .withDetail("status", "kafka-not-configured")
                        .build();
            }

            // Report UP when Kafka is configured. Actual broker connectivity is not
            // checked here to avoid blocking Kubernetes probes — operators should
            // monitor consumer group lag and partition leadership for connectivity health.
            return Health.up()
                    .withDetail("status", "bootstrap-servers-configured")
                    .withDetail("brokerCount", countBrokers(bootstrapServers))
                    .build();

        } catch (Exception e) {
            log.warn("kafka-health-check-failure reason=exception-during-check");
            return Health.down()
                    .withDetail("reason", "health-check-error")
                    .build();
        }
    }

    private static int countBrokers(String bootstrapServers) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return 0;
        }
        return bootstrapServers.split(",").length;
    }
}
