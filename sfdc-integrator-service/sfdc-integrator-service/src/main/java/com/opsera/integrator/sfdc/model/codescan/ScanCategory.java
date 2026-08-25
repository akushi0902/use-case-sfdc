package com.opsera.integrator.sfdc.model.codescan;

/**
 * Typed DTO representing a Salesforce code scan rule category.
 */
public class ScanCategory {

    private String categoryId;
    private String name;
    private int ruleCount;

    public ScanCategory() {}

    public ScanCategory(String categoryId, String name, int ruleCount) {
        this.categoryId = categoryId;
        this.name = name;
        this.ruleCount = ruleCount;
    }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRuleCount() { return ruleCount; }
    public void setRuleCount(int ruleCount) { this.ruleCount = ruleCount; }
}
