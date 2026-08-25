package com.opsera.integrator.sfdc.model.codescan;

/**
 * Typed DTO representing a single Salesforce code scan rule.
 */
public class ScanRule {

    private String ruleId;
    private String category;
    private String severity;
    private String description;

    public ScanRule() {}

    public ScanRule(String ruleId, String category, String severity, String description) {
        this.ruleId = ruleId;
        this.category = category;
        this.severity = severity;
        this.description = description;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
