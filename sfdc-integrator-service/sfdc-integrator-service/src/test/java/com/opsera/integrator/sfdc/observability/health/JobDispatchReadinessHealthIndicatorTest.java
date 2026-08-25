package com.opsera.integrator.sfdc.observability.health;

import com.opsera.integrator.sfdc.command.WorkerDispatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobDispatchReadinessHealthIndicator — unit tests")
class JobDispatchReadinessHealthIndicatorTest {

    private static WorkerDispatchProperties propertiesWithEnabled(boolean enabled, Map<String, Boolean> commandTypes) {
        WorkerDispatchProperties props = new WorkerDispatchProperties();
        props.setEnabled(enabled);
        props.setCommandTypes(commandTypes);
        return props;
    }

    @Test
    @DisplayName("returns UP with legacy-only mode when worker-dispatch is disabled")
    void health_workerDispatchDisabled_returnsUpLegacyMode() {
        WorkerDispatchProperties props = propertiesWithEnabled(false, Map.of());

        Health health = new JobDispatchReadinessHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "legacy-only");
        assertThat(health.getDetails()).containsEntry("reason", "worker-dispatch-disabled");
    }

    @Test
    @DisplayName("returns UNKNOWN when worker-dispatch enabled but no command types configured")
    void health_workerDispatchEnabledButNoCommandTypes_returnsUnknown() {
        WorkerDispatchProperties props = propertiesWithEnabled(true, Map.of());

        Health health = new JobDispatchReadinessHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason",
                "worker-dispatch-enabled-but-no-command-types-configured");
        assertThat(health.getDetails()).containsEntry("enabledCommandTypeCount", 0);
    }

    @Test
    @DisplayName("returns UP with worker-dispatch mode when command types are enabled")
    void health_workerDispatchEnabledWithCommandTypes_returnsUp() {
        WorkerDispatchProperties props = propertiesWithEnabled(true,
                Map.of("QUICK_DEPLOY", true, "VALIDATION", false));

        Health health = new JobDispatchReadinessHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "worker-dispatch");
        assertThat(health.getDetails()).containsEntry("enabledCommandTypeCount", 1);
    }

    @Test
    @DisplayName("returns UP and counts only enabled (true) command types")
    void health_countsOnlyEnabledCommandTypes() {
        WorkerDispatchProperties props = propertiesWithEnabled(true,
                Map.of("QUICK_DEPLOY", true, "VALIDATION", true, "DEPLOYMENT", false));

        Health health = new JobDispatchReadinessHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabledCommandTypeCount", 2);
    }

    @Test
    @DisplayName("returns UP legacy-only when enabled=false even with command types set")
    void health_disabledWithCommandTypes_returnsUpLegacyMode() {
        WorkerDispatchProperties props = propertiesWithEnabled(false,
                Map.of("QUICK_DEPLOY", true));

        Health health = new JobDispatchReadinessHealthIndicator(props).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "legacy-only");
    }
}
