package com.opsera.integrator.sfdc.services.prevalidate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SafeXmlParser} — XML parsing safety and size guards.
 */
@DisplayName("SafeXmlParser — safe XML parsing and XXE guards")
class SafeXmlParserTest {

    private static final int MAX_BYTES = PrevalidationContext.DEFAULT_MAX_XML_BYTES;

    @Test
    @DisplayName("parses valid package XML and returns Document")
    void parse_validPackageXml_returnsDocument() throws SafeXmlParseException {
        String xml = "<Package xmlns=\"http://soap.sforce.com/2006/04/metadata\">"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<version>58.0</version></Package>";

        Document doc = SafeXmlParser.parse(xml, MAX_BYTES);

        assertThat(doc).isNotNull();
        assertThat(doc.getElementsByTagNameNS("*", "types").getLength()).isEqualTo(1);
    }

    @Test
    @DisplayName("throws EMPTY_INPUT for null XML")
    void parse_nullXml_throwsEmptyInput() {
        assertThatThrownBy(() -> SafeXmlParser.parse(null, MAX_BYTES))
                .isInstanceOf(SafeXmlParseException.class)
                .satisfies(e -> assertThat(((SafeXmlParseException) e).getErrorCode())
                        .isEqualTo("EMPTY_INPUT"));
    }

    @Test
    @DisplayName("throws EMPTY_INPUT for blank XML")
    void parse_blankXml_throwsEmptyInput() {
        assertThatThrownBy(() -> SafeXmlParser.parse("   ", MAX_BYTES))
                .isInstanceOf(SafeXmlParseException.class)
                .satisfies(e -> assertThat(((SafeXmlParseException) e).getErrorCode())
                        .isEqualTo("EMPTY_INPUT"));
    }

    @Test
    @DisplayName("throws OVERSIZED_INPUT when XML exceeds maxBytes")
    void parse_oversizedXml_throwsOversizedInput() {
        String xml = "<Package>" + "a".repeat(1000) + "</Package>";
        assertThatThrownBy(() -> SafeXmlParser.parse(xml, 10))
                .isInstanceOf(SafeXmlParseException.class)
                .satisfies(e -> assertThat(((SafeXmlParseException) e).getErrorCode())
                        .isEqualTo("OVERSIZED_INPUT"));
    }

    @Test
    @DisplayName("throws UNSAFE_DOCTYPE when DOCTYPE declaration is present")
    void parse_doctypeDeclaration_throwsUnsafeDoctype() {
        String xml = "<!DOCTYPE Package [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<Package><types><members>Admin</members><name>Profile</name></types></Package>";
        assertThatThrownBy(() -> SafeXmlParser.parse(xml, MAX_BYTES))
                .isInstanceOf(SafeXmlParseException.class)
                .satisfies(e -> assertThat(((SafeXmlParseException) e).getErrorCode())
                        .isEqualTo("UNSAFE_DOCTYPE"));
    }

    @Test
    @DisplayName("throws MALFORMED_XML for unclosed element")
    void parse_malformedXml_throwsMalformedXml() {
        String xml = "<Package><types><members>Admin<name>Profile</name></types></Package>";
        assertThatThrownBy(() -> SafeXmlParser.parse(xml, MAX_BYTES))
                .isInstanceOf(SafeXmlParseException.class)
                .satisfies(e -> assertThat(((SafeXmlParseException) e).getErrorCode())
                        .isEqualTo("MALFORMED_XML"));
    }

    @Test
    @DisplayName("safe error message does not contain raw XML content")
    void parse_malformedXml_safeMessageDoesNotContainRawXml() {
        String xml = "<Package><secret-key>do-not-log-this</secret-key></Package>";
        // This parses fine actually (well-formed), so let's test malformed
        String malformed = "<Package><unclosed>";
        try {
            SafeXmlParser.parse(malformed, MAX_BYTES);
        } catch (SafeXmlParseException e) {
            assertThat(e.getMessage()).doesNotContain("unclosed");
            assertThat(e.getMessage()).doesNotContain("<");
        }
    }

    @Test
    @DisplayName("parses XML without namespace declaration")
    void parse_xmlWithoutNamespace_returnsDocument() throws SafeXmlParseException {
        String xml = "<Package>"
                + "<types><members>Admin</members><name>Profile</name></types>"
                + "<version>58.0</version></Package>";

        Document doc = SafeXmlParser.parse(xml, MAX_BYTES);

        assertThat(doc).isNotNull();
        assertThat(doc.getElementsByTagName("types").getLength()).isEqualTo(1);
    }
}
