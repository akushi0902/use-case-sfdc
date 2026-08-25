package com.opsera.integrator.sfdc.services.prevalidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Map;

/**
 * Reusable prevalidation service for Salesforce package XML and component selection input.
 *
 * <p>Release submission flows (deploy, validate) call {@link #validate(PrevalidationContext)}
 * before accepting a job into execution. The method returns a typed {@link PrevalidationResult}
 * whose findings are severity-classified ({@link FindingSeverity#HARD_FAILURE} or
 * {@link FindingSeverity#WARNING}).
 *
 * <p>Hard failure cases (callers must reject submission):
 * <ul>
 *   <li>Empty or blank package XML with no component context</li>
 *   <li>Package XML exceeding the configured maximum size</li>
 *   <li>XML containing a DOCTYPE declaration (potential XXE)</li>
 *   <li>Malformed XML that cannot be parsed</li>
 *   <li>Package XML with no {@code <types>} elements</li>
 * </ul>
 *
 * <p>Warning cases (callers may proceed with advisory output):
 * <ul>
 *   <li>Metadata types present in the package XML but not covered by any extraction strategy</li>
 *   <li>Duplicate component references removed during de-duplication</li>
 * </ul>
 *
 * <p>Logging safety:
 * <ul>
 *   <li>Raw package XML is never logged.</li>
 *   <li>Raw component member values are never logged; only safe references produced by
 *       the extraction strategies are used in log and finding messages.</li>
 *   <li>Log entries include only: correlationId, operationType, finding counts.</li>
 * </ul>
 */
@Service
public class PrevalidateComponentsService {

    private static final Logger log = LoggerFactory.getLogger(PrevalidateComponentsService.class);

    private final ComponentReferenceExtractor extractor;

    public PrevalidateComponentsService(ComponentReferenceExtractor extractor) {
        this.extractor = extractor;
    }

    /**
     * Validates a package XML or component selection context and returns typed findings.
     *
     * @param context the prevalidation input; must not be null
     * @return a {@link PrevalidationResult} with severity-classified findings
     */
    public PrevalidationResult validate(PrevalidationContext context) {
        if (context == null) {
            return hardFailure("NULL_CONTEXT",
                    "Prevalidation context is required",
                    "Provide a non-null PrevalidationContext with packageXml or componentType");
        }

        log.debug("Prevalidation started: correlationId='{}' operationType='{}'",
                context.getCorrelationId(), context.getOperationType());

        if (!context.hasPackageXml()) {
            return hardFailure("EMPTY_PACKAGE",
                    "No package XML provided; component selection is required for deployment",
                    "Provide a valid Salesforce package.xml with at least one <types> element");
        }

        Document document;
        try {
            document = SafeXmlParser.parse(context.getPackageXml(), context.getMaxXmlBytes());
        } catch (SafeXmlParseException e) {
            log.warn("Prevalidation XML parse failed: correlationId='{}' errorCode='{}'",
                    context.getCorrelationId(), e.getErrorCode());
            return hardFailure(e.getErrorCode(), e.getMessage(),
                    remediationForParseError(e.getErrorCode()));
        }

        ComponentReferenceExtractor.ExtractionResult extracted = extractor.extract(document);

        PrevalidationResult.Builder result = PrevalidationResult.builder();

        // Hard failure: package XML contains no <types> elements
        if (extracted.isEmpty() && extracted.unsupportedTypes().isEmpty()) {
            result.addFinding(PrevalidationFinding.builder()
                    .severity(FindingSeverity.HARD_FAILURE)
                    .messageCode("NO_TYPES_FOUND")
                    .safeSummary("Package XML contains no <types> elements")
                    .remediationHint(
                            "Add at least one <types> block with <members> and <name> to package.xml")
                    .build());
            log.warn("Prevalidation failed: correlationId='{}' reason=NO_TYPES_FOUND",
                    context.getCorrelationId());
            return result.build();
        }

        // Warning: unsupported metadata types
        for (String unsupportedType : extracted.unsupportedTypes()) {
            result.addFinding(PrevalidationFinding.builder()
                    .severity(FindingSeverity.WARNING)
                    .componentType(unsupportedType)
                    .messageCode("UNSUPPORTED_METADATA_TYPE")
                    .safeSummary("Metadata type '" + unsupportedType
                            + "' is not covered by any extraction strategy")
                    .remediationHint(
                            "Verify that '" + unsupportedType
                                    + "' is a supported Salesforce metadata type for this deployment")
                    .build());
        }

        // Warning: duplicate references removed
        if (extracted.duplicateCount() > 0) {
            result.addFinding(PrevalidationFinding.builder()
                    .severity(FindingSeverity.WARNING)
                    .messageCode("DUPLICATE_REFERENCES_REMOVED")
                    .safeSummary(extracted.duplicateCount()
                            + " duplicate component reference(s) were removed from the package")
                    .remediationHint(
                            "Review package.xml for duplicate <members> entries within the same "
                                    + "<types> block")
                    .build());
        }

        // Informational extraction summary (no finding — just log)
        for (Map.Entry<String, List<String>> entry : extracted.getReferencesByType().entrySet()) {
            log.debug("Extracted type='{}' referenceCount={}",
                    entry.getKey(), entry.getValue().size());
        }

        log.debug("Prevalidation complete: correlationId='{}' findingCount={} hasHardFailures={}",
                context.getCorrelationId(),
                result.build().getFindings().size(),
                result.build().hasHardFailures());

        return result.build();
    }

    private PrevalidationResult hardFailure(String messageCode, String safeSummary,
                                             String remediationHint) {
        return PrevalidationResult.builder()
                .addFinding(PrevalidationFinding.builder()
                        .severity(FindingSeverity.HARD_FAILURE)
                        .messageCode(messageCode)
                        .safeSummary(safeSummary)
                        .remediationHint(remediationHint)
                        .build())
                .build();
    }

    private String remediationForParseError(String errorCode) {
        return switch (errorCode) {
            case "EMPTY_INPUT" -> "Provide a non-empty Salesforce package.xml";
            case "OVERSIZED_INPUT" ->
                    "Reduce package.xml size or split into multiple smaller packages";
            case "UNSAFE_DOCTYPE" ->
                    "Remove the DOCTYPE declaration from package.xml; "
                            + "Salesforce package XML must not reference external DTDs";
            case "MALFORMED_XML" ->
                    "Fix XML syntax errors in package.xml; "
                            + "ensure all elements are properly opened and closed";
            default -> "Review package.xml for syntax and structure errors";
        };
    }
}
