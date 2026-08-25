package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import org.springframework.stereotype.Component;

/**
 * Extracts component references for Salesforce {@code Profile} metadata type.
 */
@Component
public class ProfileReferenceStrategy extends AbstractMemberReferenceStrategy {

    @Override
    public String supportedMetadataType() {
        return "Profile";
    }
}
