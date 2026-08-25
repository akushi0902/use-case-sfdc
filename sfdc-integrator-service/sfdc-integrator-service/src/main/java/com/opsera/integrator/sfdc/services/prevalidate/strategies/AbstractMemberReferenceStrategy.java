package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import com.opsera.integrator.sfdc.services.prevalidate.ReferenceExtractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Base strategy that extracts {@code <members>} element text content from a
 * Salesforce package XML {@code <types>} element.
 *
 * <p>Member references are trimmed and truncated to {@link #MAX_REF_LENGTH} characters
 * to produce bounded, log-safe strings. Empty or blank members are skipped.
 *
 * <p>Subclasses declare their {@link #supportedMetadataType()} only.
 */
abstract class AbstractMemberReferenceStrategy implements ReferenceExtractionStrategy {

    static final int MAX_REF_LENGTH = 128;

    private static final Logger log = LoggerFactory.getLogger(AbstractMemberReferenceStrategy.class);

    @Override
    public List<String> extractReferences(Element typesElement) {
        List<String> refs = new ArrayList<>();
        if (typesElement == null) {
            return refs;
        }
        try {
            NodeList members = typesElement.getElementsByTagNameNS("*", "members");
            if (members == null || members.getLength() == 0) {
                members = typesElement.getElementsByTagName("members");
            }
            for (int i = 0; i < members.getLength(); i++) {
                String text = members.item(i).getTextContent();
                if (text != null && !text.isBlank()) {
                    String safe = sanitize(text.trim());
                    if (!safe.isEmpty()) {
                        refs.add(safe);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Reference extraction failed for type '{}': category=extraction-error",
                    supportedMetadataType());
        }
        return refs;
    }

    private String sanitize(String raw) {
        // Truncate to prevent unbounded log output
        String truncated = raw.length() > MAX_REF_LENGTH ? raw.substring(0, MAX_REF_LENGTH) : raw;
        // Strip control characters that could be used for log injection
        return truncated.replaceAll("[\\p{Cntrl}]", "");
    }
}
