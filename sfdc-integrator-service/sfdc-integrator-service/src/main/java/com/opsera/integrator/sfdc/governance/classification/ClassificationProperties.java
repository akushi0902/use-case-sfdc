package com.opsera.integrator.sfdc.governance.classification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration properties for the data classification policy.
 * Bound from {@code sfdc.governance.classification} in application.yaml.
 *
 * <p>Both maps default to conservative values if not configured:
 * unknown categories resolve to {@link DataClassification#CONFIDENTIAL} and
 * {@link RetentionCategoryHint#STANDARD}.
 */
@ConfigurationProperties(prefix = "sfdc.governance.classification")
public class ClassificationProperties {

    /**
     * Maps each {@link GovernanceDataCategory} to a {@link DataClassification} tier.
     * Categories absent from this map fall back to {@link DataClassification#CONFIDENTIAL}.
     */
    private Map<GovernanceDataCategory, DataClassification> categoryMappings =
            new EnumMap<>(GovernanceDataCategory.class);

    /**
     * Maps each {@link GovernanceDataCategory} to a {@link RetentionCategoryHint}.
     * Categories absent from this map fall back to {@link RetentionCategoryHint#STANDARD}.
     */
    private Map<GovernanceDataCategory, RetentionCategoryHint> retentionHints =
            new EnumMap<>(GovernanceDataCategory.class);

    public Map<GovernanceDataCategory, DataClassification> getCategoryMappings() {
        return categoryMappings;
    }

    public void setCategoryMappings(Map<GovernanceDataCategory, DataClassification> categoryMappings) {
        this.categoryMappings = categoryMappings != null ? categoryMappings : new EnumMap<>(GovernanceDataCategory.class);
    }

    public Map<GovernanceDataCategory, RetentionCategoryHint> getRetentionHints() {
        return retentionHints;
    }

    public void setRetentionHints(Map<GovernanceDataCategory, RetentionCategoryHint> retentionHints) {
        this.retentionHints = retentionHints != null ? retentionHints : new EnumMap<>(GovernanceDataCategory.class);
    }
}
