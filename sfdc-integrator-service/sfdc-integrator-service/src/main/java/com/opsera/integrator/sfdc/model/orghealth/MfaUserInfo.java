package com.opsera.integrator.sfdc.model.orghealth;

/**
 * Typed DTO representing a user's MFA enrollment status for security compliance reporting.
 * Usernames are masked to synthetic identifiers in test fixtures.
 */
public class MfaUserInfo {

    private String username;
    private boolean mfaEnabled;

    public MfaUserInfo() {}

    public MfaUserInfo(String username, boolean mfaEnabled) {
        this.username = username;
        this.mfaEnabled = mfaEnabled;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
}
