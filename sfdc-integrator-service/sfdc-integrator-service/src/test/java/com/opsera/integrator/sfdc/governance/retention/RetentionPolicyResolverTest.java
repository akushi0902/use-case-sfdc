package com.opsera.integrator.sfdc.governance.retention;

import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RetentionPolicyResolver} (AC-5).
 *
 * <p>Uses a fixed {@link Clock} for deterministic expiration calculations.
 */
class RetentionPolicyResolverTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private RetentionPolicyProperties properties;
    private RetentionPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new RetentionPolicyProperties();
        Map<RetentionCategory, Long> durations = new EnumMap<>(RetentionCategory.class);
        durations.put(RetentionCategory.SHORT_TERM, 30L);
        durations.put(RetentionCategory.STANDARD, 365L);
        durations.put(RetentionCategory.EXTENDED, 2555L);
        durations.put(RetentionCategory.AUDIT_MANDATED, 3650L);
        properties.setDurationDays(durations);
        resolver = new RetentionPolicyResolver(properties, FIXED_CLOCK);
    }

    // ---- resolvePolicy ----

    @Test
    void resolvePolicy_standard_returns365Days() {
        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.STANDARD);
        assertThat(policy.getCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(policy.getDurationDays()).isEqualTo(365L);
    }

    @Test
    void resolvePolicy_extended_returns2555Days() {
        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.EXTENDED);
        assertThat(policy.getCategory()).isEqualTo(RetentionCategory.EXTENDED);
        assertThat(policy.getDurationDays()).isEqualTo(2555L);
    }

    @Test
    void resolvePolicy_auditMandated_returns3650Days() {
        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.AUDIT_MANDATED);
        assertThat(policy.getDurationDays()).isEqualTo(3650L);
    }

    @Test
    void resolvePolicy_shortTerm_returns30Days() {
        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.SHORT_TERM);
        assertThat(policy.getDurationDays()).isEqualTo(30L);
    }

    @Test
    void resolvePolicy_nullCategory_fallsBackToStandard() {
        RetentionPolicy policy = resolver.resolvePolicy(null);
        assertThat(policy.getCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(policy.getDurationDays()).isEqualTo(365L);
    }

    @Test
    void resolvePolicy_unconfiguredCategory_fallsBackToStandard() {
        // Remove SHORT_TERM from config to simulate missing category
        Map<RetentionCategory, Long> durations = new EnumMap<>(RetentionCategory.class);
        durations.put(RetentionCategory.STANDARD, 365L);
        properties.setDurationDays(durations);

        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.SHORT_TERM);
        assertThat(policy.getCategory()).isEqualTo(RetentionCategory.STANDARD);
    }

    @Test
    void resolvePolicy_emptyConfig_fallsBackToBuiltinDefault() {
        properties.setDurationDays(new EnumMap<>(RetentionCategory.class));
        RetentionPolicy policy = resolver.resolvePolicy(RetentionCategory.EXTENDED);
        assertThat(policy.getCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(policy.getDurationDays()).isEqualTo(RetentionPolicyResolver.DEFAULT_DURATION_DAYS);
    }

    // ---- buildMetadata ----

    @Test
    void buildMetadata_standard_setsCorrectExpiration() {
        RetentionMetadata m = resolver.buildMetadata(
                "job-001", null,
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategoryHint.STANDARD);

        assertThat(m.getJobRef()).isEqualTo("job-001");
        assertThat(m.getRetentionCategory()).isEqualTo(RetentionCategory.STANDARD);
        assertThat(m.getRetentionStart()).isEqualTo(FIXED_NOW);
        assertThat(m.getRetentionExpiration())
                .isEqualTo(FIXED_NOW.plus(365, ChronoUnit.DAYS));
    }

    @Test
    void buildMetadata_extended_setsExpirationAt2555Days() {
        RetentionMetadata m = resolver.buildMetadata(
                "artifact-001", "pipeline-fixture-abc",
                GovernanceDataCategory.SFDC_ARTIFACT,
                DataClassification.RESTRICTED,
                RetentionCategoryHint.EXTENDED);

        assertThat(m.getRetentionCategory()).isEqualTo(RetentionCategory.EXTENDED);
        assertThat(m.getRetentionExpiration())
                .isEqualTo(FIXED_NOW.plus(2555, ChronoUnit.DAYS));
        assertThat(m.getArtifactRef()).isEqualTo("pipeline-fixture-abc");
    }

    @Test
    void buildMetadata_legalHoldIsFalseByDefault() {
        RetentionMetadata m = resolver.buildMetadata(
                "job-002", null,
                GovernanceDataCategory.JOB_METADATA,
                DataClassification.CONFIDENTIAL,
                RetentionCategoryHint.STANDARD);

        assertThat(m.isLegalHold()).isFalse();
        assertThat(m.getPurgeEligibilityStatus()).isEqualTo(PurgeEligibilityStatus.INELIGIBLE);
    }

    @Test
    void buildMetadata_nullClassification_defaultsToConfidential() {
        RetentionMetadata m = resolver.buildMetadata(
                "job-003", null,
                GovernanceDataCategory.JOB_METADATA,
                null,
                RetentionCategoryHint.STANDARD);

        assertThat(m.getClassification()).isEqualTo(DataClassification.CONFIDENTIAL);
    }

    @Test
    void buildMetadata_setsTimestamps() {
        RetentionMetadata m = resolver.buildMetadata(
                "job-ts-001", null,
                GovernanceDataCategory.AUDIT_CONTEXT,
                DataClassification.CONFIDENTIAL,
                RetentionCategoryHint.AUDIT_MANDATED);

        assertThat(m.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(m.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    // ---- computeEligibility ----

    @Test
    void computeEligibility_expirationPassed_returnsEligible() {
        RetentionMetadata m = new RetentionMetadata();
        m.setLegalHold(false);
        m.setRetentionExpiration(FIXED_NOW.minus(1, ChronoUnit.DAYS));

        assertThat(resolver.computeEligibility(m)).isEqualTo(PurgeEligibilityStatus.ELIGIBLE);
    }

    @Test
    void computeEligibility_expirationNotPassed_returnsIneligible() {
        RetentionMetadata m = new RetentionMetadata();
        m.setLegalHold(false);
        m.setRetentionExpiration(FIXED_NOW.plus(100, ChronoUnit.DAYS));

        assertThat(resolver.computeEligibility(m)).isEqualTo(PurgeEligibilityStatus.INELIGIBLE);
    }

    @Test
    void computeEligibility_legalHold_returnsLegalHoldOverridesExpired() {
        RetentionMetadata m = new RetentionMetadata();
        m.setLegalHold(true);
        // Expiration in the past — should still be LEGAL_HOLD, not ELIGIBLE
        m.setRetentionExpiration(FIXED_NOW.minus(1000, ChronoUnit.DAYS));

        assertThat(resolver.computeEligibility(m)).isEqualTo(PurgeEligibilityStatus.LEGAL_HOLD);
    }

    @Test
    void computeEligibility_nullMetadata_returnsUnknown() {
        assertThat(resolver.computeEligibility(null)).isEqualTo(PurgeEligibilityStatus.UNKNOWN);
    }

    @Test
    void computeEligibility_nullExpiration_returnsUnknown() {
        RetentionMetadata m = new RetentionMetadata();
        m.setLegalHold(false);
        m.setRetentionExpiration(null);

        assertThat(resolver.computeEligibility(m)).isEqualTo(PurgeEligibilityStatus.UNKNOWN);
    }

    // ---- mapHint ----

    @Test
    void mapHint_allValues_mapCorrectly() {
        assertThat(RetentionPolicyResolver.mapHint(RetentionCategoryHint.SHORT_TERM))
                .isEqualTo(RetentionCategory.SHORT_TERM);
        assertThat(RetentionPolicyResolver.mapHint(RetentionCategoryHint.STANDARD))
                .isEqualTo(RetentionCategory.STANDARD);
        assertThat(RetentionPolicyResolver.mapHint(RetentionCategoryHint.EXTENDED))
                .isEqualTo(RetentionCategory.EXTENDED);
        assertThat(RetentionPolicyResolver.mapHint(RetentionCategoryHint.AUDIT_MANDATED))
                .isEqualTo(RetentionCategory.AUDIT_MANDATED);
    }

    @Test
    void mapHint_null_returnsStandard() {
        assertThat(RetentionPolicyResolver.mapHint(null)).isEqualTo(RetentionCategory.STANDARD);
    }

    // ---- RetentionPolicy invariants ----

    @Test
    void retentionPolicy_nullCategory_throwsIllegalArgument() {
        assertThatThrownBy(() -> new RetentionPolicy(null, 365L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionPolicy_negativeDuration_throwsIllegalArgument() {
        assertThatThrownBy(() -> new RetentionPolicy(RetentionCategory.STANDARD, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
