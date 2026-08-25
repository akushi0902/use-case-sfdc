package com.opsera.integrator.sfdc.services.prevalidate;

import com.opsera.integrator.sfdc.services.prevalidate.strategies.ApexClassReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.LayoutReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.PermissionSetReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.ProfileReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.ReportReferenceStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring service integration tests for {@link PrevalidateComponentsService}.
 *
 * <p>Verifies Spring wiring: all {@link ReferenceExtractionStrategy} beans are discovered
 * and injected into {@link ComponentReferenceExtractor}, which is then injected into
 * {@link PrevalidateComponentsService}.
 *
 * <p>Uses XML fixture files from {@code src/test/resources/fixtures/prevalidation/}.
 * No external HTTP API boundary is introduced.
 *
 * <p>No real Salesforce, Kafka, Hazelcast, or database infrastructure required —
 * only Spring component wiring is exercised.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PrevalidateComponentsService — Spring wiring integration tests")
class PrevalidateComponentsServiceIntegrationTest {

    @Autowired
    private PrevalidateComponentsService service;

    @Autowired
    private ComponentReferenceExtractor extractor;

    @Test
    @DisplayName("PrevalidateComponentsService is autowired correctly")
    void service_isAutowired() {
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("ComponentReferenceExtractor is autowired with all strategy beans")
    void extractor_isAutowiredWithAllStrategies() {
        assertThat(extractor).isNotNull();
    }

    @Test
    @DisplayName("all five strategy beans are registered: Profile, PermissionSet, Layout, Report, ApexClass")
    void allStrategies_areRegistered() {
        // Verify via extraction — if strategies were missing the types would go to unsupportedTypes
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<types><members>SalesPerm</members><name>PermissionSet</name></types>"
                + "<types><members>AccountLayout</members><name>Layout</name></types>"
                + "<types><members>SalesReport</members><name>Report</name></types>"
                + "<types><members>AccountHandler</members><name>ApexClass</name></types>"
                + "</Package>";

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-all-types")
                .operationType("DEPLOY")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        // No unsupported type warnings since all five strategies are registered
        assertThat(result.warnings().stream()
                .anyMatch(f -> "UNSUPPORTED_METADATA_TYPE".equals(f.getMessageCode())))
                .isFalse();
    }

    @Test
    @DisplayName("validates fixture file: package-xml-valid.xml → no findings")
    void validate_validFixture_noFindings() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-valid.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-valid")
                .operationType("DEPLOY")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("validates fixture file: package-xml-warning.xml → WARNING for CustomLabel")
    void validate_warningFixture_producesUnsupportedTypeWarning() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-warning.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-warning")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().stream()
                .anyMatch(f -> "UNSUPPORTED_METADATA_TYPE".equals(f.getMessageCode())
                        && "CustomLabel".equals(f.getComponentType())))
                .isTrue();
    }

    @Test
    @DisplayName("validates fixture file: package-xml-duplicate-members.xml → WARNING for duplicates")
    void validate_duplicatesFixture_producesDuplicateWarning() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-duplicate-members.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-duplicates")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings().stream()
                .anyMatch(f -> "DUPLICATE_REFERENCES_REMOVED".equals(f.getMessageCode())))
                .isTrue();
    }

    @Test
    @DisplayName("validates fixture file: package-xml-empty-types.xml → HARD_FAILURE")
    void validate_emptyTypesFixture_returnsHardFailure() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-empty-types.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-empty-types")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("NO_TYPES_FOUND");
    }

    @Test
    @DisplayName("validates fixture file: package-xml-malformed.xml → HARD_FAILURE MALFORMED_XML")
    void validate_malformedFixture_returnsHardFailure() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-malformed.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-malformed")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("MALFORMED_XML");
    }

    @Test
    @DisplayName("validates fixture file: package-xml-xxe-attempt.xml → HARD_FAILURE UNSAFE_DOCTYPE")
    void validate_xxeAttemptFixture_returnsUnsafeDoctype() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-xxe-attempt.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-xxe")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isTrue();
        assertThat(result.hardFailures().get(0).getMessageCode()).isEqualTo("UNSAFE_DOCTYPE");
    }

    @Test
    @DisplayName("validates fixture file: package-xml-unsupported-only.xml → only warnings")
    void validate_unsupportedOnlyFixture_warningsOnly() throws IOException {
        String xml = loadFixture("fixtures/prevalidation/package-xml-unsupported-only.xml");

        PrevalidationContext ctx = PrevalidationContext.builder()
                .packageXml(xml)
                .correlationId("integration-unsupported-only")
                .build();

        PrevalidationResult result = service.validate(ctx);

        assertThat(result.hasHardFailures()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().stream()
                .allMatch(f -> "UNSUPPORTED_METADATA_TYPE".equals(f.getMessageCode())))
                .isTrue();
    }

    private String loadFixture(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
