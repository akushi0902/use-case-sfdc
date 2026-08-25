package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Resolves a {@link RetentionPolicy} and computes a fully populated {@link RetentionMetadata}
 * for a given governed data category, classification, and job reference.
 *
 * <p>Design guarantees:
 * <ul>
 *   <li>Fail-closed: unknown or unconfigured categories fall back to {@link RetentionCategory#STANDARD}.
 *   <li>Deterministic: calculations use the injected {@link Clock} — no {@code Instant.now()} calls.</li>
 *   <li>Legal hold is off by default at assignment time; callers set it explicitly via
 *       {@link RetentionMetadataService#applyLegalHold}.</li>
 * </ul>
 */
@Service
public class RetentionPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyResolver.class);

    static final RetentionCategory DEFAULT_CATEGORY = RetentionCategory.STANDARD;
    static final long DEFAULT_DURATION_DAYS = 365L;

    private final RetentionPolicyProperties properties;
    private final Clock clock;

    public RetentionPolicyResolver(RetentionPolicyProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Resolves a {@link RetentionPolicy} for the given retention category.
     * Falls back to STANDARD with {@link #DEFAULT_DURATION_DAYS} when the category
     * is not configured.
     */
    public RetentionPolicy resolvePolicy(RetentionCategory category) {
        RetentionCategory effective = category != null ? category : DEFAULT_CATEGORY;
        Map<RetentionCategory, Long> durations = properties.getDurationDays();
        Long days = durations.get(effective);
        if (days == null || days <= 0) {
            log.warn("RetentionPolicyResolver: no duration configured for category={} — falling back to STANDARD/{}d",
                     effective, DEFAULT_DURATION_DAYS);
            Long standardDays = durations.get(DEFAULT_CATEGORY);
            long fallbackDays = (standardDays != null && standardDays > 0) ? standardDays : DEFAULT_DURATION_DAYS;
            return new RetentionPolicy(DEFAULT_CATEGORY, fallbackDays);
        }
        return new RetentionPolicy(effective, days);
    }

    /**
     * Builds a fully populated {@link RetentionMetadata} for a job record at creation time.
     *
     * @param jobRef      opaque job reference (jobId or pipelineId) — no raw PII
     * @param artifactRef optional artifact or pipeline reference (may be null)
     * @param category    governance data category
     * @param classification data classification tier
     * @param retentionHint retention category hint from classification policy
     * @return populated metadata with retentionStart=now, retentionExpiration=now+durationDays,
     *         legalHold=false, purgeEligibilityStatus=INELIGIBLE (expiration not yet reached)
     */
    public RetentionMetadata buildMetadata(String jobRef,
                                           String artifactRef,
                                           GovernanceDataCategory category,
                                           DataClassification classification,
                                           RetentionCategoryHint retentionHint) {
        RetentionCategory retentionCategory = mapHint(retentionHint);
        RetentionPolicy policy = resolvePolicy(retentionCategory);

        Instant now = clock.instant();
        Instant expiration = now.plus(policy.getDurationDays(), ChronoUnit.DAYS);

        RetentionMetadata metadata = new RetentionMetadata();
        metadata.setJobRef(jobRef);
        metadata.setArtifactRef(artifactRef);
        metadata.setDataCategory(category);
        metadata.setClassification(classification != null ? classification : DataClassification.CONFIDENTIAL);
        metadata.setRetentionCategory(policy.getCategory());
        metadata.setRetentionStart(now);
        metadata.setRetentionExpiration(expiration);
        metadata.setLegalHold(false);
        metadata.setPurgeEligibilityStatus(PurgeEligibilityStatus.INELIGIBLE);
        metadata.setCreatedAt(now);
        metadata.setUpdatedAt(now);
        return metadata;
    }

    /**
     * Recomputes the purge eligibility status for a record using the current clock.
     * LEGAL_HOLD always overrides ELIGIBLE.
     */
    public PurgeEligibilityStatus computeEligibility(RetentionMetadata metadata) {
        if (metadata == null) {
            return PurgeEligibilityStatus.UNKNOWN;
        }
        if (metadata.isLegalHold()) {
            return PurgeEligibilityStatus.LEGAL_HOLD;
        }
        if (metadata.getRetentionExpiration() == null) {
            return PurgeEligibilityStatus.UNKNOWN;
        }
        Instant now = clock.instant();
        return now.isAfter(metadata.getRetentionExpiration())
                ? PurgeEligibilityStatus.ELIGIBLE
                : PurgeEligibilityStatus.INELIGIBLE;
    }

    /**
     * Maps a {@link RetentionCategoryHint} to a {@link RetentionCategory}.
     * Unknown hints fall back to STANDARD.
     */
    static RetentionCategory mapHint(RetentionCategoryHint hint) {
        if (hint == null) {
            return DEFAULT_CATEGORY;
        }
        return switch (hint) {
            case SHORT_TERM -> RetentionCategory.SHORT_TERM;
            case STANDARD -> RetentionCategory.STANDARD;
            case EXTENDED -> RetentionCategory.EXTENDED;
            case AUDIT_MANDATED -> RetentionCategory.AUDIT_MANDATED;
        };
    }
}
