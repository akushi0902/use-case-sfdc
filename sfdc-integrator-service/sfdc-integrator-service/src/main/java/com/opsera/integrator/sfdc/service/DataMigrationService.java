package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.DataMigrationRequest;

/**
 * Service interface for legacy data migration operations.
 */
public interface DataMigrationService {

    void migrate(DataMigrationRequest request);
}
