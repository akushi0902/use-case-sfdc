package com.opsera.integrator.sfdc.services.prevalidate;

import org.w3c.dom.Element;

import java.util.List;

/**
 * Strategy interface for extracting safe component references from a Salesforce
 * package XML {@code <types>} element.
 *
 * <p>Each implementation handles one Salesforce metadata type (e.g. Profile, PermissionSet)
 * and produces a list of safe, bounded component reference strings suitable for use in
 * structured log fields and prevalidation finding messages.
 *
 * <p>Implementations must not log or return raw XML fragments, credentials, or
 * full user-supplied strings without sanitization.
 */
public interface ReferenceExtractionStrategy {

    /**
     * Returns the Salesforce metadata type name this strategy handles,
     * e.g. {@code "Profile"}, {@code "PermissionSet"}, {@code "Layout"}.
     *
     * @return non-null, non-empty metadata type name
     */
    String supportedMetadataType();

    /**
     * Extracts safe component reference strings from a {@code <types>} DOM element.
     *
     * <p>The element contains one or more {@code <members>} child elements and a
     * single {@code <name>} element. Implementations should iterate the members and
     * return their text content after applying any type-specific sanitization.
     *
     * <p>If {@code typesElement} is null or contains no members, return an empty list.
     * Never throw — log a warning and return an empty list on unexpected structure.
     *
     * @param typesElement a {@code <types>} DOM element from a package XML document
     * @return list of safe component reference strings; never null
     */
    List<String> extractReferences(Element typesElement);
}
