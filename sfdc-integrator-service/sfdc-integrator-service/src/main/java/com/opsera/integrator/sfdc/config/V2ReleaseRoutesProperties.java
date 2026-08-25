package com.opsera.integrator.sfdc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for v2 release API canary and fallback controls.
 *
 * <p>Bound from {@code sfdc.v2.release.routes.*} in application.yaml.
 *
 * <p>Routing policy:
 * <ul>
 *   <li>Master {@code enabled} flag is evaluated first. When {@code false}, all v2 submission
 *       requests are denied regardless of per-operation flags.</li>
 *   <li>Per-operation flags in {@code operations} are evaluated only when the master flag is
 *       {@code true}. Unknown or unconfigured operation types fail closed (denied by default).</li>
 *   <li>{@code read-only-status-fallback} controls whether status lookup remains available
 *       when submission is disabled (default: {@code true}).</li>
 * </ul>
 *
 * <p>Rollback procedure:
 * <ol>
 *   <li>Set {@code sfdc.v2.release.routes.enabled=false} in the deployment configuration.</li>
 *   <li>Restart the service (or trigger a live-reload if supported).</li>
 *   <li>Legacy routes at {@code /quickdeploy} and {@code /deploy} continue to serve traffic
 *       — they are never affected by this flag.</li>
 * </ol>
 *
 * <p>Sample application.yaml:
 * <pre>
 * sfdc:
 *   v2:
 *     release:
 *       routes:
 *         enabled: true
 *         operations:
 *           QUICK_DEPLOY: true
 *           DEPLOY: true
 *           VALIDATE: true
 *           CANCEL: false
 *         read-only-status-fallback: true
 * </pre>
 */
@ConfigurationProperties(prefix = "sfdc.v2.release.routes")
public class V2ReleaseRoutesProperties {

    /** Master kill switch. V2 submission is denied unless this is true. */
    private boolean enabled = true;

    /**
     * Per-operation allow-list. Keys are operation type names (e.g. "QUICK_DEPLOY").
     * Default-deny: an operation type that is absent or mapped to false is blocked.
     */
    private Map<String, Boolean> operations = new HashMap<>();

    /**
     * When true, GET status lookups remain available even when v2 submission is globally disabled.
     * Allows monitoring of previously submitted jobs during a rollback window.
     */
    private boolean readOnlyStatusFallback = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Boolean> getOperations() { return operations; }
    public void setOperations(Map<String, Boolean> operations) {
        this.operations = operations != null ? operations : new HashMap<>();
    }

    public boolean isReadOnlyStatusFallback() { return readOnlyStatusFallback; }
    public void setReadOnlyStatusFallback(boolean readOnlyStatusFallback) {
        this.readOnlyStatusFallback = readOnlyStatusFallback;
    }

    /**
     * Returns true if the given operation type is permitted under current configuration.
     *
     * <p>Global {@code enabled=false} overrides all per-operation flags. Unknown or
     * unconfigured operation types fail closed — they are denied even when the global
     * flag is true.
     *
     * @param operationType case-sensitive operation type string (e.g. "QUICK_DEPLOY")
     * @return true if the operation is permitted
     */
    public boolean isOperationEnabled(String operationType) {
        if (!enabled) {
            return false;
        }
        if (operationType == null || operationType.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(operations.get(operationType));
    }
}
