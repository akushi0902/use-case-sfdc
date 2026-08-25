package com.opsera.integrator.sfdc.model.orghealth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed response DTO for org metadata type counts from a cached collection run.
 */
public class MetadataCounts {

    private String customerId;
    private String toolId;
    private Map<String, Integer> counts;

    public MetadataCounts() {
        this.counts = new LinkedHashMap<>();
    }

    public MetadataCounts(String customerId, String toolId, Map<String, Integer> counts) {
        this.customerId = customerId;
        this.toolId = toolId;
        this.counts = counts != null ? new LinkedHashMap<>(counts) : new LinkedHashMap<>();
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public Map<String, Integer> getCounts() { return Collections.unmodifiableMap(counts); }
    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts != null ? new LinkedHashMap<>(counts) : new LinkedHashMap<>();
    }
}
