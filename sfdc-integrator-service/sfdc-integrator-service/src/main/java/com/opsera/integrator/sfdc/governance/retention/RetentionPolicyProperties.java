package com.opsera.integrator.sfdc.governance.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration properties for retention policy periods.
 * Bound from {@code sfdc.governance.retention} in application.yaml.
 *
 * <p>The {@code duration-days} map assigns a retention window (in days) to each
 * {@link RetentionCategory}. Categories absent from the map fall back to the
 * STANDARD policy at runtime — callers must not assume an unconfigured category
 * means no expiration.
 *
 * <p>Unknown categories in {@link RetentionPolicyResolver} fail closed to the
 * STANDARD duration, which must always be configured.
 */
@ConfigurationProperties(prefix = "sfdc.governance.retention")
public class RetentionPolicyProperties {

    /**
     * Maps each {@link RetentionCategory} to a retention window in days.
     * STANDARD must always be set — it is the fail-closed default.
     * Minimum sensible values (GDPR/CCPA baseline):
     *   SHORT_TERM: 30, STANDARD: 365, EXTENDED: 2555 (7 years), AUDIT_MANDATED: 3650 (10 years)
     */
    private Map<RetentionCategory, Long> durationDays = new EnumMap<>(RetentionCategory.class);

    public Map<RetentionCategory, Long> getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Map<RetentionCategory, Long> durationDays) {
        this.durationDays = durationDays != null ? durationDays : new EnumMap<>(RetentionCategory.class);
    }
}
