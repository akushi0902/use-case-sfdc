package com.opsera.integrator.sfdc.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for legacy Apex test class discovery operations.
 */
public interface SfdcTestClassService {

    List<String> getTestClasses(String pipelineId, String stepId);

    Map<String, List<String>> getTestClassMapping(String pipelineId, String stepId);
}
