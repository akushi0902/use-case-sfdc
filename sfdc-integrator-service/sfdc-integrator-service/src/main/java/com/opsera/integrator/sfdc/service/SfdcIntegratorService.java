package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.DeployRequest;

/**
 * Service interface for legacy SFDC deployment and validation operations.
 */
public interface SfdcIntegratorService {

    /** Initiates a Salesforce metadata deployment. */
    void deploy(DeployRequest request);

    /** Validates a pending Salesforce deployment without committing it. */
    void validate(DeployRequest request);
}
