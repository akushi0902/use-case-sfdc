package com.opsera.integrator.sfdc.observability.health;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for Hazelcast cluster participation.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>If {@link HazelcastInstance} is not wired (Hazelcast auto-configuration is excluded
 *       in the test profile) → {@code UNKNOWN} with a safe reason code.</li>
 *   <li>If Hazelcast is running → {@code UP} with bounded cluster size and state details.</li>
 *   <li>If Hazelcast lifecycle is terminated or not running → {@code DOWN} with a safe reason.</li>
 * </ul>
 *
 * <p>Health details are bounded and safe — no cluster member addresses, network endpoints,
 * partition IDs, or raw configuration values are included.
 *
 * <p>Registered as {@code hazelcastCluster} in health group configuration.
 */
@Component("hazelcastClusterHealthIndicator")
public class HazelcastClusterHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HazelcastClusterHealthIndicator.class);

    private final HazelcastInstance hazelcastInstance;

    public HazelcastClusterHealthIndicator(@Nullable HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Health health() {
        try {
            if (hazelcastInstance == null) {
                return Health.unknown()
                        .withDetail("reason", "hazelcast-not-configured")
                        .withDetail("status", "hazelcast-auto-configuration-excluded")
                        .build();
            }

            LifecycleService lifecycleService = hazelcastInstance.getLifecycleService();
            if (!lifecycleService.isRunning()) {
                return Health.down()
                        .withDetail("reason", "hazelcast-lifecycle-terminated")
                        .withDetail("state", "NOT_RUNNING")
                        .build();
            }

            int clusterSize = hazelcastInstance.getCluster().getMembers().size();
            return Health.up()
                    .withDetail("state", "ACTIVE")
                    .withDetail("clusterSize", clusterSize)
                    .build();

        } catch (Exception e) {
            log.warn("hazelcast-health-check-failure reason=exception-during-check");
            return Health.down()
                    .withDetail("reason", "health-check-error")
                    .build();
        }
    }
}
