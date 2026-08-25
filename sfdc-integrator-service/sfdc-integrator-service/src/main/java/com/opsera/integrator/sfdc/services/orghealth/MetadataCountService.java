package com.opsera.integrator.sfdc.services.orghealth;

import com.opsera.integrator.sfdc.model.orghealth.MetadataCounts;

import java.util.Optional;

/**
 * Service interface for org metadata type count retrieval from cached collection runs.
 */
public interface MetadataCountService {

    Optional<MetadataCounts> getMetadataCounts(String customerId, String toolId);
}
