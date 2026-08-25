package com.opsera.integrator.sfdc.governance.retention;

/**
 * Immutable value object pairing a {@link RetentionCategory} with the configured
 * retention period in days.
 *
 * <p>Instances are resolved by {@link RetentionPolicyResolver} from
 * {@link RetentionPolicyProperties}. A duration of {@code 0} means the category
 * has not been configured — callers should treat this as fail-closed
 * (use the STANDARD or most-restrictive policy instead).
 */
public final class RetentionPolicy {

    private final RetentionCategory category;
    private final long durationDays;

    public RetentionPolicy(RetentionCategory category, long durationDays) {
        if (category == null) {
            throw new IllegalArgumentException("RetentionCategory must not be null");
        }
        if (durationDays < 0) {
            throw new IllegalArgumentException("durationDays must be non-negative");
        }
        this.category = category;
        this.durationDays = durationDays;
    }

    public RetentionCategory getCategory() {
        return category;
    }

    public long getDurationDays() {
        return durationDays;
    }

    @Override
    public String toString() {
        return "RetentionPolicy{category=" + category + ", durationDays=" + durationDays + "}";
    }
}
