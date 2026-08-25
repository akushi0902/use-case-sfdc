package com.opsera.integrator.sfdc.services.prevalidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches package XML extraction to registered {@link ReferenceExtractionStrategy} beans.
 *
 * <p>For each {@code <types>} element in the parsed package XML document, the extractor:
 * <ol>
 *   <li>Reads the {@code <name>} child element to determine the metadata type.</li>
 *   <li>Looks up the registered strategy for that type.</li>
 *   <li>Delegates to the strategy to extract safe component reference strings.</li>
 *   <li>For unsupported types, records them in {@link ExtractionResult#unsupportedTypes()}
 *       so callers can produce advisory warnings.</li>
 * </ol>
 *
 * <p>Duplicate component references within the same metadata type are de-duplicated
 * in the result. The original count (before de-duplication) is preserved in
 * {@link ExtractionResult#duplicateCount()} to allow operator diagnosis.
 *
 * <p>No raw XML fragments, raw member values before sanitization, or parsing internals
 * are logged. Log entries use only: metadata type name, component count.
 */
@Component
public class ComponentReferenceExtractor {

    private static final Logger log = LoggerFactory.getLogger(ComponentReferenceExtractor.class);

    private final Map<String, ReferenceExtractionStrategy> strategyByType;

    public ComponentReferenceExtractor(List<ReferenceExtractionStrategy> strategies) {
        this.strategyByType = strategies.stream()
                .collect(Collectors.toMap(
                        ReferenceExtractionStrategy::supportedMetadataType,
                        Function.identity(),
                        (a, b) -> {
                            log.warn("Duplicate extraction strategy for type '{}'; keeping first",
                                    a.supportedMetadataType());
                            return a;
                        },
                        LinkedHashMap::new));
    }

    /**
     * Extracts component references from a parsed package XML document.
     *
     * @param document a safely-parsed Salesforce package XML document
     * @return extraction result with references, unsupported types, and de-duplication stats
     */
    public ExtractionResult extract(Document document) {
        Map<String, List<String>> refsByType = new LinkedHashMap<>();
        List<String> unsupportedTypes = new ArrayList<>();
        int rawTotal = 0;
        int dedupedTotal = 0;

        NodeList typesList = document.getElementsByTagNameNS("*", "types");
        if (typesList.getLength() == 0) {
            typesList = document.getElementsByTagName("types");
        }

        for (int i = 0; i < typesList.getLength(); i++) {
            Element typesElement = (Element) typesList.item(i);
            String metadataType = resolveMetadataType(typesElement);
            if (metadataType == null || metadataType.isBlank()) {
                log.warn("Package XML types element missing <name> child; skipping");
                continue;
            }

            ReferenceExtractionStrategy strategy = strategyByType.get(metadataType);
            if (strategy == null) {
                log.debug("No extraction strategy for metadata type '{}'; recording as unsupported",
                        metadataType);
                if (!unsupportedTypes.contains(metadataType)) {
                    unsupportedTypes.add(metadataType);
                }
                continue;
            }

            List<String> raw = strategy.extractReferences(typesElement);
            rawTotal += raw.size();

            List<String> deduped = raw.stream()
                    .distinct()
                    .collect(Collectors.toList());
            dedupedTotal += deduped.size();

            refsByType.merge(metadataType, deduped, (existing, incoming) -> {
                List<String> merged = new ArrayList<>(existing);
                merged.addAll(incoming);
                return merged.stream().distinct().collect(Collectors.toList());
            });

            log.debug("Extracted {} reference(s) for type '{}'", deduped.size(), metadataType);
        }

        return new ExtractionResult(refsByType, unsupportedTypes, rawTotal - dedupedTotal);
    }

    private String resolveMetadataType(Element typesElement) {
        NodeList nameNodes = typesElement.getElementsByTagNameNS("*", "name");
        if (nameNodes.getLength() == 0) {
            nameNodes = typesElement.getElementsByTagName("name");
        }
        if (nameNodes.getLength() == 0) {
            return null;
        }
        String text = nameNodes.item(0).getTextContent();
        return text != null ? text.trim() : null;
    }

    /**
     * Result of a component reference extraction pass.
     */
    public static final class ExtractionResult {

        private final Map<String, List<String>> referencesByType;
        private final List<String> unsupportedTypes;
        private final int duplicateCount;

        ExtractionResult(Map<String, List<String>> referencesByType,
                         List<String> unsupportedTypes,
                         int duplicateCount) {
            this.referencesByType = referencesByType;
            this.unsupportedTypes = unsupportedTypes;
            this.duplicateCount = duplicateCount;
        }

        /** Map of metadata type name to de-duplicated safe component references. */
        public Map<String, List<String>> getReferencesByType() {
            return referencesByType;
        }

        /** Metadata types present in the package XML but not handled by any strategy. */
        public List<String> unsupportedTypes() {
            return unsupportedTypes;
        }

        /** Number of duplicate references removed during de-duplication. */
        public int duplicateCount() {
            return duplicateCount;
        }

        /** True if no references were extracted and no unsupported types found. */
        public boolean isEmpty() {
            return referencesByType.isEmpty() && unsupportedTypes.isEmpty();
        }

        /** Total count of de-duplicated references across all types. */
        public int totalReferenceCount() {
            return referencesByType.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }
    }
}
