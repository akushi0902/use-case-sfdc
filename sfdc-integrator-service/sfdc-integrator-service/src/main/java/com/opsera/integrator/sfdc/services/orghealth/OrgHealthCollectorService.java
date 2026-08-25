package com.opsera.integrator.sfdc.services.orghealth;

import com.opsera.integrator.sfdc.model.orghealth.OrgHealthCollectRequest;
import com.opsera.integrator.sfdc.model.orghealth.OrgHealthResponse;

import java.util.Optional;

/**
 * Service interface for org health data collection and cached report retrieval.
 */
public interface OrgHealthCollectorService {

    void collect(OrgHealthCollectRequest request);

    Optional<OrgHealthResponse> getOrgHealthReport(String customerId, String toolId);
}
