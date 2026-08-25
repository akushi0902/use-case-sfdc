package com.opsera.integrator.sfdc.services.prevalidate;

import com.opsera.integrator.sfdc.services.prevalidate.strategies.ApexClassReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.LayoutReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.PermissionSetReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.ProfileReferenceStrategy;
import com.opsera.integrator.sfdc.services.prevalidate.strategies.ReportReferenceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ComponentReferenceExtractor} — strategy dispatch and de-duplication.
 */
@DisplayName("ComponentReferenceExtractor — strategy dispatch and de-duplication")
class ComponentReferenceExtractorTest {

    private ComponentReferenceExtractor extractor;

    @BeforeEach
    void setUp() {
        List<ReferenceExtractionStrategy> strategies = List.of(
                new ProfileReferenceStrategy(),
                new PermissionSetReferenceStrategy(),
                new LayoutReferenceStrategy(),
                new ReportReferenceStrategy(),
                new ApexClassReferenceStrategy()
        );
        extractor = new ComponentReferenceExtractor(strategies);
    }

    @Test
    @DisplayName("extracts Profile members from namespace-aware package XML")
    void extract_profileMembers_returnsSafeRefs() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types>"
                + "<members>Admin</members><members>Standard User</members>"
                + "<name>Profile</name>"
                + "</types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.getReferencesByType()).containsKey("Profile");
        assertThat(result.getReferencesByType().get("Profile")).containsExactlyInAnyOrder(
                "Admin", "Standard User");
    }

    @Test
    @DisplayName("extracts multiple supported types from same package XML")
    void extract_multipleTypes_returnsAllRefs() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<types><members>SalesPermSet</members><name>PermissionSet</name></types>"
                + "<types><members>AccountHandler</members><name>ApexClass</name></types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.getReferencesByType()).containsKeys("Profile", "PermissionSet", "ApexClass");
        assertThat(result.totalReferenceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("records unsupported types without crashing")
    void extract_unsupportedType_recordsWarning() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>MyFlow</members><name>Flow</name></types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.unsupportedTypes()).containsExactly("Flow");
        assertThat(result.getReferencesByType()).isEmpty();
    }

    @Test
    @DisplayName("de-duplicates members within the same type")
    void extract_duplicateMembers_deduplicates() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types>"
                + "<members>Admin</members><members>Admin</members><members>Standard User</members>"
                + "<name>Profile</name>"
                + "</types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.getReferencesByType().get("Profile"))
                .containsExactlyInAnyOrder("Admin", "Standard User");
        assertThat(result.duplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns empty extraction for package XML with no types")
    void extract_noTypesElements_returnsEmpty() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<version>58.0</version>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.totalReferenceCount()).isEqualTo(0);
        assertThat(result.unsupportedTypes()).isEmpty();
    }

    @Test
    @DisplayName("handles package XML without namespace using getElementsByTagName")
    void extract_noNamespace_stillExtractsRefs() throws SafeXmlParseException {
        String xml = "<Package>"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.getReferencesByType()).containsKey("Profile");
    }

    @Test
    @DisplayName("Layout and Report strategies extract members correctly")
    void extract_layoutAndReport_returnsRefs() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Account-Account Layout</members><name>Layout</name></types>"
                + "<types><members>MyReport</members><name>Report</name></types>"
                + "</Package>";
        Document doc = SafeXmlParser.parse(xml, PrevalidationContext.DEFAULT_MAX_XML_BYTES);

        ComponentReferenceExtractor.ExtractionResult result = extractor.extract(doc);

        assertThat(result.getReferencesByType().get("Layout")).containsExactly("Account-Account Layout");
        assertThat(result.getReferencesByType().get("Report")).containsExactly("MyReport");
    }
}
