package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import org.springframework.stereotype.Component;

/**
 * Extracts component references for Salesforce {@code ApexClass} metadata type.
 */
@Component
public class ApexClassReferenceStrategy extends AbstractMemberReferenceStrategy {

    @Override
    public String supportedMetadataType() {
        return "ApexClass";
    }
}
