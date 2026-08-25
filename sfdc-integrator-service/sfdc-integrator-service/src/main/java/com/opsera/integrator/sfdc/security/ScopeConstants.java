package com.opsera.integrator.sfdc.security;

/**
 * Centralized scope name constants for service-level capability authorization.
 *
 * <p>Scope values are lowercase to match the normalized storage in {@link CallerContext}.
 * Controllers and tests must reference these constants rather than inline strings to
 * prevent accidental scope drift.
 *
 * <p>Scope naming convention: {@code <domain>.<capability>}
 */
public final class ScopeConstants {

    /** Required scope to initiate a quick deploy job via POST /quickdeploy. */
    public static final String QUICK_DEPLOY_SUBMIT = "quickdeploy.submit";

    /** Required scope to cancel a running quick deploy job via POST /quickdeploy/stop. */
    public static final String QUICK_DEPLOY_CANCEL = "quickdeploy.cancel";

    private ScopeConstants() {}
}
