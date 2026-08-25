package com.opsera.integrator.sfdc.command;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot configuration properties for worker dispatch routing.
 *
 * <p>Bound from {@code sfdc.worker-dispatch.*} in application.yaml.
 * Master kill switch: {@code enabled} must be {@code true} AND the per-command-type
 * flag must also be {@code true} for worker dispatch to activate.
 *
 * <p>All flags default to {@code false} — worker dispatch is opt-in and disabled by default.
 * To enable worker dispatch for specific command types, set:
 * <pre>
 * sfdc:
 *   worker-dispatch:
 *     enabled: true
 *     command-types:
 *       QUICK_DEPLOY: true
 * </pre>
 */
@ConfigurationProperties(prefix = "sfdc.worker-dispatch")
public class WorkerDispatchProperties {

    /** Master kill switch. Worker dispatch is disabled unless this is true. */
    private boolean enabled = false;

    /** Per-command-type flags. Keys match ReleaseOperationType names. */
    private Map<String, Boolean> commandTypes = new HashMap<>();

    /** Default timeout for worker-dispatched jobs, in seconds. */
    private int timeoutSeconds = 1800;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Boolean> getCommandTypes() { return commandTypes; }
    public void setCommandTypes(Map<String, Boolean> commandTypes) {
        this.commandTypes = commandTypes != null ? commandTypes : new HashMap<>();
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    /**
     * Returns true if worker dispatch is enabled globally AND for the given command type.
     *
     * @param commandType case-sensitive operation type string (e.g. "QUICK_DEPLOY")
     */
    public boolean isWorkerDispatchEnabled(String commandType) {
        if (!enabled) {
            return false;
        }
        return Boolean.TRUE.equals(commandTypes.get(commandType));
    }
}
