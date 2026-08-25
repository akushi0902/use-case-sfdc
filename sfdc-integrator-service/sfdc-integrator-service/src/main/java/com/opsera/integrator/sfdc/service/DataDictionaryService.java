package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.DataMigrationRequest;

/**
 * Service interface for legacy data dictionary generation operations.
 */
public interface DataDictionaryService {

    void generateDataDictionary(DataMigrationRequest request);
}
