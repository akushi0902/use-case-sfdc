package com.opsera.integrator.sfdc.security;

import java.util.regex.Pattern;

/**
 * Allow-list profiles for shell-bound argument validation.
 *
 * <p>Each profile defines the maximum permitted length and a strict allow-list
 * regex pattern for the corresponding field category. Only characters explicitly
 * in the pattern are permitted; everything else is rejected before values can
 * influence command construction or process invocation.
 *
 * <p>Profiles are intentionally narrow — branch names, file paths, deployment
 * identifiers, and generic labels each have different safe character sets.
 * Using a single generic profile would either over-restrict valid inputs or
 * under-restrict unsafe ones.
 */
public enum ShellArgumentProfile {

    /**
     * Deployment request identifiers (e.g. Salesforce deployment IDs, job IDs).
     * Allow: alphanumeric, hyphens, underscores. Must start with alphanumeric.
     */
    DEPLOYMENT_IDENTIFIER(256, "^[a-zA-Z0-9][a-zA-Z0-9\\-_]*$"),

    /**
     * Task and step identifiers (e.g. CI step IDs, fallback task references).
     * Allow: alphanumeric, hyphens, underscores, dots. Must start with alphanumeric.
     */
    TASK_IDENTIFIER(256, "^[a-zA-Z0-9][a-zA-Z0-9\\-_\\.]*$"),

    /**
     * Git branch names.
     * Allow: alphanumeric, hyphens, underscores, forward slashes, dots.
     * Must start with alphanumeric. No double dots (..), no trailing slash.
     * Note: RELATIVE_PATH traversal check is applied independently.
     */
    BRANCH_NAME(256, "^[a-zA-Z0-9][a-zA-Z0-9\\-_\\./]*$"),

    /**
     * Single file names without directory separators.
     * Allow: alphanumeric, hyphens, underscores, dots. Must start with alphanumeric.
     * Forward slashes are not permitted — use RELATIVE_PATH for path segments.
     */
    FILE_NAME(256, "^[a-zA-Z0-9][a-zA-Z0-9\\-_\\.]*$"),

    /**
     * Relative file system paths within a known working directory.
     * Allow: alphanumeric, hyphens, underscores, dots, forward slashes.
     * Must start with alphanumeric (no leading slash). Path traversal (..)
     * is rejected by the validator independently of this pattern.
     */
    RELATIVE_PATH(512, "^[a-zA-Z0-9][a-zA-Z0-9\\-_\\./]*$"),

    /**
     * Generic labels, correlation identifiers, and short safe tokens.
     * Allow: alphanumeric, hyphens, underscores, colons. Must start with alphanumeric.
     * More restrictive than DEPLOYMENT_IDENTIFIER — colons allowed, dots and slashes not.
     */
    GENERIC_LABEL(128, "^[a-zA-Z0-9][a-zA-Z0-9\\-_:]*$");

    private final int maxLength;
    private final Pattern allowListPattern;

    ShellArgumentProfile(int maxLength, String regex) {
        this.maxLength = maxLength;
        this.allowListPattern = Pattern.compile(regex);
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Pattern getAllowListPattern() {
        return allowListPattern;
    }
}
