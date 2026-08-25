package com.opsera.integrator.sfdc.services.prevalidate.strategies;

import org.springframework.stereotype.Component;

/**
 * Extracts component references for Salesforce {@code PermissionSet} metadata type.
 */
@Component
public class PermissionSetReferenceStrategy extends AbstractMemberReferenceStrategy {

    @Override
    public String supportedMetadataType() {
        return "PermissionSet";
    }
}
