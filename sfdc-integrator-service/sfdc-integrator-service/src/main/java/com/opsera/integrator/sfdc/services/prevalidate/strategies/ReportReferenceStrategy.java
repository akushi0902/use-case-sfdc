package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import org.springframework.stereotype.Component;

/**
 * Extracts component references for Salesforce {@code Report} metadata type.
 */
@Component
public class ReportReferenceStrategy extends AbstractMemberReferenceStrategy {

    @Override
    public String supportedMetadataType() {
        return "Report";
    }
}
