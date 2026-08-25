package com.opsera.integrator.sfdc.controller;

import com.opsera.integrator.sfdc.logging.SafeLogEvent;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.orghealth.MetadataCounts;
import com.opsera.integrator.sfdc.model.orghealth.MfaUserInfo;
import com.opsera.integrator.sfdc.model.orghealth.OrgHealthCollectRequest;
import com.opsera.integrator.sfdc.model.orghealth.OrgHealthResponse;
import com.opsera.integrator.sfdc.services.orghealth.MetadataCountService;
import com.opsera.integrator.sfdc.services.orghealth.OrgHealthCollectorService;
import com.opsera.integrator.sfdc.services.orghealth.SecurityComplianceCollector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Legacy REST controller for Salesforce org health, hygiene, and compliance metrics.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /org-health/collect — trigger a new org health data collection run</li>
 *   <li>GET  /org-health/info — retrieve the cached org health report by customerId and toolId</li>
 *   <li>GET  /org-health/users-without-mfa — retrieve users without MFA enabled</li>
 *   <li>GET  /org-health/metadata-counts — retrieve org metadata type counts</li>
 * </ul>
 *
 * <p>Operational logging uses {@link SafeStructuredLogger} — only selected operational
 * identifiers (customerId, toolId) are emitted; org URLs, user lists, and raw request
 * objects are never logged.
 */
@RestController
@RequestMapping("/org-health")
public class OrgHealthController {

    static final String SUCCESS = "SUCCESS";

    private final OrgHealthCollectorService orgHealthCollectorService;
    private final SecurityComplianceCollector securityComplianceCollector;
    private final MetadataCountService metadataCountService;
    private final SafeStructuredLogger safeLogger;

    public OrgHealthController(OrgHealthCollectorService orgHealthCollectorService,
                                SecurityComplianceCollector securityComplianceCollector,
                                MetadataCountService metadataCountService,
                                SafeStructuredLogger safeLogger) {
        this.orgHealthCollectorService = orgHealthCollectorService;
        this.securityComplianceCollector = securityComplianceCollector;
        this.metadataCountService = metadataCountService;
        this.safeLogger = safeLogger;
    }

    /**
     * Triggers an org health data collection run.
     * Logs customerId and toolId only — orgUrl and apiVersion are not logged.
     */
    @PostMapping("/collect")
    public ResponseEntity<String> collect(@RequestBody OrgHealthCollectRequest request) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("org-health-collect")
                .controller("OrgHealthController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("customerId", request.getCustomerId())
                .safeField("toolId", request.getToolId())
                .build());

        orgHealthCollectorService.collect(request);
        return ResponseEntity.ok(SUCCESS);
    }

    /**
     * Returns the cached org health report for the given customer and tool.
     * Returns HTTP 404 when no cached report is available.
     */
    @GetMapping("/info")
    public ResponseEntity<OrgHealthResponse> getOrgHealthInfo(
            @RequestParam String customerId,
            @RequestParam String toolId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("org-health-info")
                .controller("OrgHealthController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("customerId", customerId)
                .safeField("toolId", toolId)
                .build());

        return orgHealthCollectorService.getOrgHealthReport(customerId, toolId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the list of users without MFA enabled for the given customer and tool.
     * Returns an empty list when all users have MFA enabled.
     */
    @GetMapping("/users-without-mfa")
    public ResponseEntity<List<MfaUserInfo>> getUsersWithoutMfa(
            @RequestParam String customerId,
            @RequestParam String toolId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("org-health-users-without-mfa")
                .controller("OrgHealthController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("customerId", customerId)
                .safeField("toolId", toolId)
                .build());

        List<MfaUserInfo> users = securityComplianceCollector.getUsersWithoutMfa(customerId, toolId);
        return ResponseEntity.ok(users);
    }

    /**
     * Returns the cached org metadata type counts for the given customer and tool.
     * Returns HTTP 404 when no cached count data is available.
     */
    @GetMapping("/metadata-counts")
    public ResponseEntity<MetadataCounts> getMetadataCounts(
            @RequestParam String customerId,
            @RequestParam String toolId) {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("org-health-metadata-counts")
                .controller("OrgHealthController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("customerId", customerId)
                .safeField("toolId", toolId)
                .build());

        return metadataCountService.getMetadataCounts(customerId, toolId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
