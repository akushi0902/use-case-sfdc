package com.opsera.integrator.sfdc.security;

/**
 * Thrown when a request value fails shell argument safety validation.
 *
 * <p><strong>Security invariant:</strong> the rejected raw value is NEVER stored in
 * or accessible from this exception. Only the field name, profile, and rejection
 * category are retained so that logs and error responses can include safe diagnostic
 * context without leaking attacker-controlled strings into logs or responses.
 *
 * <p>Handled by {@code SfdcExceptionHandler} which maps this to HTTP 400
 * with error code {@code SHELL_UNSAFE_INPUT} and a structured field-level error.
 */
public class ShellArgumentViolationException extends RuntimeException {

    private final String fieldName;
    private final ShellArgumentProfile profile;
    private final String rejectionCategory;

    /**
     * @param fieldName        the request field that failed validation (safe to log)
     * @param profile          the allow-list profile that was applied
     * @param rejectionCategory a short category token describing why validation failed
     *                          (e.g. "command-separator-or-metacharacter", "path-traversal",
     *                          "oversized-value", "null-byte-injection", "allow-list-violation")
     */
    public ShellArgumentViolationException(String fieldName, ShellArgumentProfile profile,
                                            String rejectionCategory) {
        super("Shell argument violation: field=" + fieldName
                + " profile=" + profile.name()
                + " category=" + rejectionCategory);
        this.fieldName = fieldName;
        this.profile = profile;
        this.rejectionCategory = rejectionCategory;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ShellArgumentProfile getProfile() {
        return profile;
    }

    public String getRejectionCategory() {
        return rejectionCategory;
    }

    /**
     * Safe string representation — never includes the rejected value.
     */
    @Override
    public String toString() {
        return "ShellArgumentViolationException{field=" + fieldName
                + ", profile=" + profile.name()
                + ", category=" + rejectionCategory + "}";
    }
}
