package com.opsera.integrator.sfdc.services.prevalidate;

import com.opsera.integrator.sfdc.services.prevalidate.strategies.ApexClassReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.PermissionSetReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.ProfileReferenceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PrevalidateComponentsService}.
 *
 * <p>Covers: empty context, malformed XML, XXE attempt, oversized input, empty types, valid
 * package XML, unsupported types (warnings), duplicate references (warnings), and safe summary
 * generation (no raw XML in finding messages).
 */
@DisplayName("PrevalidateComponentsService — prevalidation classification and safe summaries")
class PrevalidateComponentsServiceTest {

    private PrevalidateComponentsService service;

    @BeforeEach
    void setUp() {
        ComponentReferenceExtractor extractor = new ComponentReferenceExtractor(List.of(
                new ProfileReferenceStrategy(),
                new PermissionSetReferenceStrategy(),
                new ApexClassReferenceStrategy()
        ));
        service = new PrevalidateComponentsService(extractor);
    }

    // ── Hard failure: null context ────────────────────────────────────────────

    @Test
    @DisplayName("null context returns HARD_FAILURE with NULL_CONTEXT code")
    void validate_nullContext_returnsHardFailure() {
        PrevalidationResult result = service.validate(null);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures()).hasSize(1);
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("NULL_CONTEXT");
    }

    // ── Hard failure: empty/blank packageXml ──────────────────────────────────

    @Test
    @DisplayName("empty packageXml returns HARD_FAILURE with EMPTY_PACKAGE code")
    void validate_emptyPackageXml_returnsHardFailure() {
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml("")
                .correlationId("corr-001")
                .operationType("DEPLOY")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("EMPTY_PACKAGE");
        assertThat(result.hardFailures().get(0).getRemediationHint()).isNotBlank();
    }

    @Test
    @DisplayName("null packageXml (no xml set) returns HARD_FAILURE with EMPTY_PACKAGE code")
    void validate_noPackageXml_returnsHardFailure() {
        PrevalidationContext ctx = PrevalidationContext.builder()
                .operationType("VALIDATE")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("EMPTY_PACKAGE");
    }

    // ── Hard failure: malformed XML ───────────────────────────────────────────

    @Test
    @DisplayName("malformed XML returns HARD_FAILURE with MALFORMED_XML code")
    void validate_malformedXml_returnsHardFailure() {
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml("<Package><unclosed>")
                .correlationId("corr-002")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("MALFORMED_XML");
    }

    @Test
    @DisplayName("malformed XML finding safeSummary does not contain raw XML fragment")
    void validate_malformedXml_safeSummaryNoRawXml() {
        String maliciousFragment = "<inject-me/>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml("<Package>" + maliciousFragment)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        String summary = result.hardFailures().get(0).getSafeSummary();
        assertThat(summary).doesNotContain(maliciousFragment);
        assertThat(summary).doesNotContain("<inject-me");
    }

    // ── Hard failure: XXE attempt ─────────────────────────────────────────────

    @Test
    @DisplayName("DOCTYPE declaration in XML returns HARD_FAILURE with UNSAFE_DOCTYPE code")
    void validate_doctypeXml_returnsUnsafeDoctype() {
        String xml = "<!DOCTYPE Package [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<Package><types><members>&xxe;</members><name>Profile</name></types></Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("corr-003")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("UNSAFE_DOCTYPE");
    }

    // ── Hard failure: oversized XML ───────────────────────────────────────────

    @Test
    @DisplayName("oversized XML returns HARD_FAILURE with OVERSIZED_INPUT code")
    void validate_oversizedXml_returnsOversizedInput() {
        String bigXml = "<Package>" + "a".repeat(2000) + "</Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(bigXml)
                .maxXmlBytes(100)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("OVERSIZED_INPUT");
    }

    // ── Hard failure: no types ────────────────────────────────────────────────

    @Test
    @DisplayName("package XML with no types elements returns HARD_FAILURE with NO_TYPES_FOUND")
    void validate_noTypesElements_returnsNoTypesFound() {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<version>58.0</version></Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("NO_TYPES_FOUND");
        assertThat(result.hardFailures().get(0).getRemediationHint()).contains("package.xml");
    }

    // ── Pass: valid package XML ───────────────────────────────────────────────

    @Test
    @DisplayName("valid package XML with supported types passes with no findings")
    void validate_validPackageXml_noFindings() {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<version>58.0</version></Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("corr-pass-001")
                .operationType("DEPLOY")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.getFindings()).isEmpty();
        assertThat(result.isPassed()).isTrue();
    }

    // ── Warning: unsupported metadata type ───────────────────────────────────

    @Test
    @DisplayName("unsupported metadata type produces WARNING finding")
    void validate_unsupportedType_producesWarning() {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<types><members>MyFlow</members><name>Flow</name></types>"
                + "</Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).getMessageCode()).isEqualTo("UNSUPPORTED_METADATA_TYPE");
        assertThat(result.warnings().get(0).getComponentType()).isEqualTo("Flow");
    }

    // ── Warning: duplicate references ────────────────────────────────────────

    @Test
    @DisplayName("duplicate members produce WARNING finding with DUPLICATE_REFERENCES_REMOVED code")
    void validate_duplicateMembers_producesDuplicateWarning() {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types>"
                + "<members>Admin</members><members>Admin</members>"
                + "<name>Profile</name>"
                + "</types>"
                + "</Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings().stream()
                .anyMatch(f -> "DUPLICATE_REFERENCES_REMOVED".equals(f.getMessageCode())))
                .isTrue();
    }

    // ── Safe summary: no raw values ───────────────────────────────────────────

    @Test
    @DisplayName("finding safeSummary for unsupported type uses metadata type name only")
    void validate_unsupportedType_safeSummaryUsesTypeName() {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>SomeValue</members><name>CustomLabel</name></types>"
                + "</Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .build();

        PrevalidationResult result = service.validate(ctx);

        PrevalidationFinding finding = result.warnings().stream()
                .filter(f -> "UNSUPPORTED_METADATA_TYPE".equals(f.getMessageCode()))
                .findFirst()
                .orElseThrow();
        // Safe summary should contain the type name but NOT the member value
        assertThat(finding.getSafeSummary()).contains("CustomLabel");
        assertThat(finding.getSafeSummary()).doesNotContain("SomeValue");
    }

    // ── Finding severity distinguishability ──────────────────────────────────

    @Test
    @DisplayName("hard failures and warnings are distinguishable in the same result")
    void validate_mixedFindings_distinguishableByType() {
        // Only unsupported types → warnings only, no hard failures
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<types><members>MyLabel</members><name>CustomLabel</name></types>"
                + "<types>"
                + "<members>Admin</members><members>Admin</members>"
                + "<name>PermissionSet</name>"
                + "</types>"
                + "</Package>";
        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.hardFailures()).isEmpty();
    }
}
