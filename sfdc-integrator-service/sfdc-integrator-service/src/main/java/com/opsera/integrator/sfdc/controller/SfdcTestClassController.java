package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.service.SfdcTestClassService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Legacy REST controller for Salesforce Apex test class discovery.
 *
 * <p>Routes:
 * <ul>
 *   <li>GET /apex/testclasses         — retrieve Apex test class list for a pipeline step</li>
 *   <li>GET /apex/testclasses/mapping — retrieve Apex test class mapping for a pipeline step</li>
 * </ul>
 *
 * <p>Returns typed list or mapping responses. Always returns HTTP 200 (empty list or empty
 * map for missing data — never 404) since "no test classes found" is a valid discovery state.
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — raw request objects, Apex class
 * names from Salesforce metadata, and discovery payloads are never emitted to logs.
 */
@RestController
@RequestMapping("/apex/testclasses")
public class SfdcTestClassController {

    private final SfdcTestClassService testClassService;
    private final SafeStructuredLogger safeLogger;

    public SfdcTestClassController(SfdcTestClassService testClassService,
                                    SafeStructuredLogger safeLogger) {
        this.testClassService = testClassService;
        this.safeLogger = safeLogger;
    }

    /** Returns the Apex test class list for the given pipeline step. Always HTTP 200. */
    @GetMapping
    public ResponseEntity<List<String>> getTestClasses(
            @RequestParam String pipelineId,
            @RequestParam String stepId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("apex-testclasses-list")
                .controller("SfdcTestClassController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", pipelineId)
                .safeField("stepId", stepId)
                .build());

        return ResponseEntity.ok(testClassService.getTestClasses(pipelineId, stepId));
    }

    /** Returns the Apex test class mapping for the given pipeline step. Always HTTP 200. */
    @GetMapping("/mapping")
    public ResponseEntity<Map<String, List<String>>> getTestClassMapping(
            @RequestParam String pipelineId,
            @RequestParam String stepId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("apex-testclasses-mapping")
                .controller("SfdcTestClassController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", pipelineId)
                .safeField("stepId", stepId)
                .build());

        return ResponseEntity.ok(testClassService.getTestClassMapping(pipelineId, stepId));
    }
}
