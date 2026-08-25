package com.opsera.integrator.sfdc.services.prevalidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

/**
 * Safe XML parser utility for Salesforce package XML input.
 *
 * <p>Hardened against:
 * <ul>
 *   <li>XML External Entity (XXE) injection — DOCTYPE declarations and external entity
 *       resolution are disabled.</li>
 *   <li>Oversized payloads — input size is checked before parsing; payloads exceeding
 *       {@code maxBytes} produce a {@link SafeXmlParseException}.</li>
 *   <li>Malformed XML — parser errors are caught and converted to
 *       {@link SafeXmlParseException} without leaking parser stack traces.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe. Use {@link #parse(String, int)}.
 *
 * <p>Never log the raw XML input — log only safe diagnostic fields (correlationId,
 * input size in bytes, error category).
 */
public final class SafeXmlParser {

    private static final Logger log = LoggerFactory.getLogger(SafeXmlParser.class);

    // Feature strings for XXE hardening
    private static final String FEATURE_DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL =
            "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private SafeXmlParser() {}

    /**
     * Parses a package XML string safely.
     *
     * @param xml      the raw XML input; must not be null
     * @param maxBytes maximum allowed byte length before parsing is refused
     * @return the parsed DOM Document
     * @throws SafeXmlParseException if the input is null, blank, oversized, contains a DOCTYPE
     *                               declaration, or is malformed XML
     */
    public static Document parse(String xml, int maxBytes) throws SafeXmlParseException {
        if (xml == null || xml.isBlank()) {
            throw new SafeXmlParseException("EMPTY_INPUT", "Package XML input is empty or blank");
        }

        int byteLength = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (byteLength > maxBytes) {
            log.warn("Package XML rejected: size={} bytes exceeds maxBytes={}", byteLength, maxBytes);
            throw new SafeXmlParseException("OVERSIZED_INPUT",
                    "Package XML exceeds maximum allowed size of " + maxBytes + " bytes");
        }

        // Heuristic check for DOCTYPE before parsing — the XML parser may not fire
        // FEATURE_DISALLOW_DOCTYPE on all versions if the feature is not supported.
        String trimmed = xml.stripLeading();
        if (trimmed.contains("<!DOCTYPE") || trimmed.contains("<!doctype")) {
            log.warn("Package XML rejected: DOCTYPE declaration detected (possible XXE attempt)");
            throw new SafeXmlParseException("UNSAFE_DOCTYPE",
                    "Package XML must not contain a DOCTYPE declaration");
        }

        try {
            DocumentBuilderFactory factory = createSecureFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Suppress default stderr error output from the parser
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (SafeXmlParseException rethrow) {
            throw rethrow;
        } catch (SAXException e) {
            log.warn("Package XML parse failed: category=malformed-xml");
            throw new SafeXmlParseException("MALFORMED_XML",
                    "Package XML could not be parsed: malformed structure");
        } catch (IOException e) {
            log.warn("Package XML parse failed: category=read-error");
            throw new SafeXmlParseException("READ_ERROR",
                    "Package XML could not be read");
        } catch (ParserConfigurationException e) {
            log.error("XML parser configuration failed: category=config-error");
            throw new SafeXmlParseException("PARSER_CONFIG_ERROR",
                    "XML parser could not be configured securely");
        }
    }

    private static DocumentBuilderFactory createSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Disable DOCTYPE and external entity resolution (XXE hardening)
        setFeatureSafely(factory, FEATURE_DISALLOW_DOCTYPE, true);
        setFeatureSafely(factory, FEATURE_EXTERNAL_GENERAL, false);
        setFeatureSafely(factory, FEATURE_EXTERNAL_PARAMETER, false);
        setFeatureSafely(factory, FEATURE_LOAD_EXTERNAL_DTD, false);

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        // Additional hardening via XMLConstants
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Some parsers don't support these attributes; the feature flags above suffice
        }

        return factory;
    }

    private static void setFeatureSafely(DocumentBuilderFactory factory,
                                          String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Feature not supported by this parser — the DOCTYPE heuristic check acts as fallback
        }
    }
}
