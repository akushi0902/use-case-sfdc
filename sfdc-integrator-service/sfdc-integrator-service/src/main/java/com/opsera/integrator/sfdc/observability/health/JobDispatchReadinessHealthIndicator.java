package com.opsera.integrator.sfdc.observability.health;

import com.opsera.integrator.sfdc.command.WorkerDispatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Actuator health indicator for release job dispatch readiness.
 *
 * <p>Reports whether the service is configured to accept and dispatch release jobs.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Worker dispatch disabled (master kill switch off) → {@code UP} with mode {@code legacy-only}.
 *       All traffic uses the legacy execution path, which is the expected production state.</li>
 *   <li>Worker dispatch enabled and at least one command type is configured → {@code UP} with
 *       mode {@code worker-dispatch} and the count of enabled command types.</li>
 *   <li>Worker dispatch enabled but no command types configured → {@code UNKNOWN} because
 *       the service cannot route traffic through either the legacy or dispatch path reliably.</li>
 * </ul>
 *
 * <p>Health details are bounded and safe — no Kubernetes endpoint addresses, credentials,
 * or namespace values are included.
 *
 * <p>Registered as {@code jobDispatchReadiness} in health group configuration.
 */
@Component("jobDispatchReadinessHealthIndicator")
public class JobDispatchReadinessHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(JobDispatchReadinessHealthIndicator.class);

    private final WorkerDispatchProperties workerDispatchProperties;

    public JobDispatchReadinessHealthIndicator(WorkerDispatchProperties workerDispatchProperties) {
        this.workerDispatchProperties = workerDispatchProperties;
    }

    @Override
    public Health health() {
        try {
            if (!workerDispatchProperties.isEnabled()) {
                return Health.up()
                        .withDetail("mode", "legacy-only")
                        .withDetail("reason", "worker-dispatch-disabled")
                        .build();
            }

            List<String> enabledTypes = workerDispatchProperties.getCommandTypes().entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> entry.getKey().toUpperCase())
                    .sorted()
                    .collect(Collectors.toList());

            if (enabledTypes.isEmpty()) {
                return Health.unknown()
                        .withDetail("mode", "worker-dispatch")
                        .withDetail("reason", "worker-dispatch-enabled-but-no-command-types-configured")
                        .withDetail("enabledCommandTypeCount", 0)
                        .build();
            }

            return Health.up()
                    .withDetail("mode", "worker-dispatch")
                    .withDetail("enabledCommandTypeCount", enabledTypes.size())
                    .build();

        } catch (Exception e) {
            log.warn("job-dispatch-health-check-failure reason=exception-during-check");
            return Health.down()
                    .withDetail("reason", "health-check-error")
                    .build();
        }
    }
}
