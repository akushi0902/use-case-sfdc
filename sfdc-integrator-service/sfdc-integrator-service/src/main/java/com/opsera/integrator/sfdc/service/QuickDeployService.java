package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.model.QuickDeployStopRequest;

/**
 * Service interface for legacy quick deploy operations.
 * Long-running Salesforce quick deploy work is delegated asynchronously.
 */
public interface QuickDeployService {

    /** Initiates a quick deploy operation for the given request. */
    void start(QuickDeployRequest request);

    /** Cancels an in-progress quick deploy operation. */
    void stop(QuickDeployStopRequest request);
}
