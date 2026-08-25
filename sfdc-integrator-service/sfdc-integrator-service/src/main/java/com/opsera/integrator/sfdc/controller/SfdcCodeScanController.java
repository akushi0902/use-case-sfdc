package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.codescan.QualityGateResult;
import com.opsera.integrator.sfdc.model.codescan.ScanCategory;
import com.opsera.integrator.sfdc.model.codescan.ScanRequest;
import com.opsera.integrator.sfdc.model.codescan.ScanRule;
import com.opsera.integrator.sfdc.model.codescan.ScanSummary;
import com.opsera.integrator.sfdc.services.codescan.SfdcCodeScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Legacy REST controller for Salesforce code scan and quality gate reporting.
 *
 * <p>Routes:
 * <ul>
 *   <li>GET  /code-scan/rules             — retrieve all scan rules</li>
 *   <li>GET  /code-scan/rules/by-category — retrieve scan rules filtered by category</li>
 *   <li>GET  /code-scan/categories        — retrieve all scan rule categories</li>
 *   <li>POST /code-scan/submit            — submit a code scan job (ACCEPTED acknowledgement)</li>
 *   <li>GET  /code-scan/summary           — retrieve cached scan summary by pipelineId</li>
 *   <li>GET  /code-scan/quality-gate      — retrieve quality gate result by pipelineId</li>
 * </ul>
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — repository URLs, branch names,
 * raw request objects, and scan findings are never emitted to logs.
 */
@RestController
@RequestMapping("/code-scan")
public class SfdcCodeScanController {

    static final String ACCEPTED = "ACCEPTED";

    private final SfdcCodeScanService codeScanService;
    private final SafeStructuredLogger safeLogger;

    public SfdcCodeScanController(SfdcCodeScanService codeScanService,
                                   SafeStructuredLogger safeLogger) {
        this.codeScanService = codeScanService;
        this.safeLogger = safeLogger;
    }

    /** Returns all configured scan rules. */
    @GetMapping("/rules")
    public ResponseEntity<List<ScanRule>> getAllRules() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-rules")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        return ResponseEntity.ok(codeScanService.getAllRules());
    }

    /** Returns scan rules filtered by category name. */
    @GetMapping("/rules/by-category")
    public ResponseEntity<List<ScanRule>> getRulesByCategory(@RequestParam String category) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-rules-by-category")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("category", category)
                .build());

        return ResponseEntity.ok(codeScanService.getRulesByCategory(category));
    }

    /** Returns all scan rule categories. */
    @GetMapping("/categories")
    public ResponseEntity<List<ScanCategory>> getCategories() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-categories")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        return ResponseEntity.ok(codeScanService.getCategories());
    }

    /**
     * Submits a code scan job for asynchronous processing.
     * Returns ACCEPTED as a checkpoint-style acknowledgement.
     * Logs pipelineId and stepId only — repositoryUrl and branch are not logged.
     */
    @PostMapping("/submit")
    public ResponseEntity<String> submitScan(@RequestBody ScanRequest request) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-submit")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", request.getPipelineId())
                .safeField("stepId", request.getStepId())
                .build());

        codeScanService.submitScan(request);
        return ResponseEntity.ok(ACCEPTED);
    }

    /**
     * Returns the cached scan summary for the given pipelineId.
     * Returns HTTP 404 when no cached summary is available.
     */
    @GetMapping("/summary")
    public ResponseEntity<ScanSummary> getScanSummary(@RequestParam String pipelineId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-summary")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", pipelineId)
                .build());

        return codeScanService.getScanSummary(pipelineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the quality gate evaluation result for the given pipelineId.
     * Returns HTTP 404 when no quality gate result is available.
     */
    @GetMapping("/quality-gate")
    public ResponseEntity<QualityGateResult> getQualityGateResult(@RequestParam String pipelineId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("code-scan-quality-gate")
                .controller("SfdcCodeScanController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", pipelineId)
                .build());

        return codeScanService.getQualityGateResult(pipelineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
