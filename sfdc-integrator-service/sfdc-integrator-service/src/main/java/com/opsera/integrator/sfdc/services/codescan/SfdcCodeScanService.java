package com.opsera.integrator.sfdc.services.codescan;

import com.opsera.integrator.sfdc.model.codescan.QualityGateResult;
import com.opsera.integrator.sfdc.model.codescan.ScanCategory;
import com.opsera.integrator.sfdc.model.codescan.ScanRequest;
import com.opsera.integrator.sfdc.model.codescan.ScanRule;
import com.opsera.integrator.sfdc.model.codescan.ScanSummary;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Salesforce code scan operations including rule retrieval,
 * category listing, scan submission, summary lookup, and quality gate evaluation.
 */
public interface SfdcCodeScanService {

    List<ScanRule> getAllRules();

    List<ScanRule> getRulesByCategory(String category);

    List<ScanCategory> getCategories();

    void submitScan(ScanRequest request);

    Optional<ScanSummary> getScanSummary(String pipelineId);

    Optional<QualityGateResult> getQualityGateResult(String pipelineId);
}
