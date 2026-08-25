package com.opsera.integrator.sfdc.governance;

import com.opsera.integrator.sfdc.governance.classification.ClassificationContext;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.governance.classification.ClassificationProperties;
import com.opsera.integrator.sfdc.governance.classification.DataClassification;
import com.opsera.integrator.sfdc.governance.classification.GovernanceDataCategory;
import com.opsera.integrator.sfdc.governance.classification.RetentionCategoryHint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link ClassificationPolicyResolver}.
 * No Spring context required — resolver is instantiated with test properties directly (AC-4).
 */
class ClassificationPolicyResolverTest {

    private ClassificationPolicyResolver resolver;
    private ClassificationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ClassificationProperties();

        Map<GovernanceDataCategory, DataClassification> mappings =
                new EnumMap<>(GovernanceDataCategory.class);
        mappings.put(GovernanceDataCategory.JOB_METADATA, DataClassification.CONFIDENTIAL);
        mappings.put(GovernanceDataCategory.LIFECYCLE_CHECKPOINT, DataClassification.CONFIDENTIAL);
        mappings.put(GovernanceDataCategory.SFDC_ARTIFACT, DataClassification.RESTRICTED);
        mappings.put(GovernanceDataCategory.OPERATIONAL_LOG, DataClassification.INTERNAL);
        mappings.put(GovernanceDataCategory.AUDIT_CONTEXT, DataClassification.CONFIDENTIAL);
        mappings.put(GovernanceDataCategory.CACHE_ENTRY, DataClassification.INTERNAL);
        properties.setCategoryMappings(mappings);

        Map<GovernanceDataCategory, RetentionCategoryHint> hints =
                new EnumMap<>(GovernanceDataCategory.class);
        hints.put(GovernanceDataCategory.JOB_METADATA, RetentionCategoryHint.STANDARD);
        hints.put(GovernanceDataCategory.LIFECYCLE_CHECKPOINT, RetentionCategoryHint.STANDARD);
        hints.put(GovernanceDataCategory.SFDC_ARTIFACT, RetentionCategoryHint.EXTENDED);
        hints.put(GovernanceDataCategory.OPERATIONAL_LOG, RetentionCategoryHint.SHORT_TERM);
        hints.put(GovernanceDataCategory.AUDIT_CONTEXT, RetentionCategoryHint.EXTENDED);
        hints.put(GovernanceDataCategory.CACHE_ENTRY, RetentionCategoryHint.SHORT_TERM);
        properties.setRetentionHints(hints);

        resolver = new ClassificationPolicyResolver(properties);
    }

    @Test
    void resolve_jobMetadata_returnsConfidentialStandard() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.JOB_METADATA, "quick-deploy-start");
        assertNotNull(ctx);
        assertEquals(DataClassification.CONFIDENTIAL, ctx.getClassification());
        assertEquals(RetentionCategoryHint.STANDARD, ctx.getRetentionHint());
        assertEquals("quick-deploy-start", ctx.getOperationName());
        assertEquals(GovernanceDataCategory.JOB_METADATA, ctx.getCategory());
    }

    @Test
    void resolve_sfdcArtifact_returnsRestrictedExtended() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.SFDC_ARTIFACT, "deploy");
        assertNotNull(ctx);
        assertEquals(DataClassification.RESTRICTED, ctx.getClassification());
        assertEquals(RetentionCategoryHint.EXTENDED, ctx.getRetentionHint());
    }

    @Test
    void resolve_operationalLog_returnsInternalShortTerm() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.OPERATIONAL_LOG, "health-check");
        assertNotNull(ctx);
        assertEquals(DataClassification.INTERNAL, ctx.getClassification());
        assertEquals(RetentionCategoryHint.SHORT_TERM, ctx.getRetentionHint());
    }

    @Test
    void resolve_cacheEntry_returnsInternalShortTerm() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.CACHE_ENTRY, "session-lookup");
        assertNotNull(ctx);
        assertEquals(DataClassification.INTERNAL, ctx.getClassification());
        assertEquals(RetentionCategoryHint.SHORT_TERM, ctx.getRetentionHint());
    }

    @Test
    void resolve_auditContext_returnsConfidentialExtended() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.AUDIT_CONTEXT, "audit-write");
        assertNotNull(ctx);
        assertEquals(DataClassification.CONFIDENTIAL, ctx.getClassification());
        assertEquals(RetentionCategoryHint.EXTENDED, ctx.getRetentionHint());
    }

    @Test
    void resolve_nullCategory_failsClosedToConfidential() {
        ClassificationContext ctx = resolver.resolve(null, "unknown-op");
        assertNotNull(ctx, "Resolver must never return null");
        assertEquals(DataClassification.CONFIDENTIAL, ctx.getClassification(),
                "Null category must fail closed to CONFIDENTIAL");
        assertEquals(RetentionCategoryHint.STANDARD, ctx.getRetentionHint(),
                "Null category must default to STANDARD retention");
    }

    @Test
    void resolve_unconfiguredCategory_failsClosedToConfidential() {
        // Remove JOB_METADATA from the mappings to simulate an unconfigured category
        properties.getCategoryMappings().remove(GovernanceDataCategory.JOB_METADATA);
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.JOB_METADATA, "unconfigured-op");
        assertNotNull(ctx);
        assertEquals(DataClassification.CONFIDENTIAL, ctx.getClassification(),
                "Unconfigured category must fail closed to CONFIDENTIAL");
    }

    @Test
    void resolve_emptyProperties_allCategoriesDefaultToConfidential() {
        ClassificationPolicyResolver emptyResolver =
                new ClassificationPolicyResolver(new ClassificationProperties());
        for (GovernanceDataCategory category : GovernanceDataCategory.values()) {
            ClassificationContext ctx = emptyResolver.resolve(category, "empty-config-op");
            assertNotNull(ctx);
            assertEquals(DataClassification.CONFIDENTIAL, ctx.getClassification(),
                    "Empty config must fail closed: " + category);
        }
    }

    @Test
    void resolve_operationName_propagatedToContext() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.JOB_METADATA, "post-refresh");
        assertEquals("post-refresh", ctx.getOperationName());
    }

    @Test
    void resolve_nullOperationName_defaultsToUnknown() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.JOB_METADATA, null);
        assertNotNull(ctx.getOperationName());
        assertEquals("unknown", ctx.getOperationName());
    }

    @Test
    void classificationContext_toString_doesNotExposePayload() {
        ClassificationContext ctx = resolver.resolve(GovernanceDataCategory.SFDC_ARTIFACT, "deploy");
        String str = ctx.toString();
        // toString must contain classification metadata only
        assertNotNull(str);
        assertTrue(str.contains("RESTRICTED"), "toString must include classification tier");
        assertTrue(str.contains("deploy"), "toString must include operation name");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
