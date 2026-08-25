package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import org.springframework.stereotype.Component;

/**
 * Extracts component references for Salesforce {@code Layout} metadata type.
 */
@Component
public class LayoutReferenceStrategy extends AbstractMemberReferenceStrategy {

    @Override
    public String supportedMetadataType() {
        return "Layout";
    }
}
