package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.DataMigrationRequest;

/**
 * Service interface for legacy data migration operations.
 */
public interface DataMigrationService {

    void migrate(DataMigrationRequest request);

    void listCustomSettings(DataMigrationRequest request);

    void listFieldProperties(DataMigrationRequest request);

    void validateSoql(DataMigrationRequest request);

    void getOrgLimits(DataMigrationRequest request);

    void getSoqlDetails(DataMigrationRequest request);

    void getDependentSObjects(DataMigrationRequest request);

    void listSObjects(DataMigrationRequest request);

    void listManagedPackages(DataMigrationRequest request);

    void listFilterableFields(DataMigrationRequest request);

    void validateMigrationEntities(DataMigrationRequest request);
}
