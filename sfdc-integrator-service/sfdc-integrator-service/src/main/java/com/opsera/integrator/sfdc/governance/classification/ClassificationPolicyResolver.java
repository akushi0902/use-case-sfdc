package com.opsera.integrator.sfdc.governance.classification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves a {@link ClassificationContext} for a given governed data category and operation.
 *
 * <p>Policy is driven by {@link ClassificationProperties} mappings loaded from
 * {@code sfdc.governance.classification} in application.yaml. This design allows
 * unit tests to instantiate the resolver with any properties without starting Spring.
 *
 * <p>Fail-closed guarantee: any unrecognized or null category resolves to
 * {@link DataClassification#CONFIDENTIAL} and {@link RetentionCategoryHint#STANDARD}.
 * Errors in the resolver must never propagate to callers; they are logged (operation name
 * only, no payload) and a safe default context is returned.
 */
@Service
public class ClassificationPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(ClassificationPolicyResolver.class);

    static final DataClassification DEFAULT_CLASSIFICATION = DataClassification.CONFIDENTIAL;
    static final RetentionCategoryHint DEFAULT_RETENTION = RetentionCategoryHint.STANDARD;

    private final ClassificationProperties properties;

    public ClassificationPolicyResolver(ClassificationProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the classification context for the given category and operation name.
     *
     * <p>Never returns null. Falls back to {@link DataClassification#CONFIDENTIAL} for
     * unknown categories or resolver errors.
     *
     * @param category      the governed data category (null treated as unknown)
     * @param operationName safe operation label for structured logging (no payload content)
     * @return resolved classification context, never null
     */
    public ClassificationContext resolve(GovernanceDataCategory category, String operationName) {
        try {
            DataClassification classification = resolveClassification(category);
            RetentionCategoryHint retention = resolveRetention(category);
            return new ClassificationContext(category, classification, retention, operationName);
        } catch (Exception ex) {
            log.warn("Classification resolver error: operation={}, category={} — defaulting to CONFIDENTIAL",
                    operationName, category);
            return new ClassificationContext(category, DEFAULT_CLASSIFICATION, DEFAULT_RETENTION, operationName);
        }
    }

    private DataClassification resolveClassification(GovernanceDataCategory category) {
        if (category == null) {
            return DEFAULT_CLASSIFICATION;
        }
        return properties.getCategoryMappings().getOrDefault(category, DEFAULT_CLASSIFICATION);
    }

    private RetentionCategoryHint resolveRetention(GovernanceDataCategory category) {
        if (category == null) {
            return DEFAULT_RETENTION;
        }
        return properties.getRetentionHints().getOrDefault(category, DEFAULT_RETENTION);
    }
}
