package com.opsera.integrator.sfdc.security;

import org.springframework.stereotype.Component;

/**
 * Reusable allow-list validator for values that may reach shell execution.
 *
 * <p>Validates a string argument against a named {@link ShellArgumentProfile} that
 * defines the maximum permitted length and a strict allow-list character pattern.
 * The following checks are applied unconditionally, regardless of profile:
 *
 * <ol>
 *   <li>Null and blank rejection</li>
 *   <li>Maximum length enforcement (profile-specific)</li>
 *   <li>Null byte ({@code \0}) rejection — null bytes can terminate C strings and
 *       bypass downstream length checks in native code</li>
 *   <li>Newline and carriage return ({@code \n}, {@code \r}) rejection — can inject
 *       additional shell commands or HTTP header values</li>
 *   <li>Command separator and metacharacter rejection ({@code ;}, {@code |}, {@code &},
 *       {@code `}, {@code $}, {@code !}) — primary shell injection vectors</li>
 *   <li>Path traversal sequence ({@code ..}) rejection — prevents directory escape</li>
 *   <li>Allow-list pattern match (profile-specific regex)</li>
 * </ol>
 *
 * <p><strong>Security note:</strong> the rejected raw value is never passed to
 * {@link ShellArgumentViolationException} — only the field name, profile, and a
 * safe rejection category token are carried. This prevents attacker-controlled
 * strings from appearing in logs, error responses, or stack traces.
 */
@Component
public class ShellArgumentValidator {

    private static final char NULL_BYTE = '\0';
    private static final String PATH_TRAVERSAL_SEQUENCE = "..";

    private static final char[] UNSAFE_CHARS = {
        '\n', '\r',       // newline injection
        ';', '|', '&',   // command separators
        '`', '$', '!'    // shell metacharacters (command substitution, variable expansion, history)
    };

    /**
     * Validates a required shell-bound argument.
     *
     * @param value     the value to validate (must not be null or blank)
     * @param fieldName the request field name, included in the exception for safe logging
     * @param profile   the allow-list profile to apply
     * @throws ShellArgumentViolationException if any safety check fails
     */
    public void validate(String value, String fieldName, ShellArgumentProfile profile) {
        if (value == null) {
            throw new ShellArgumentViolationException(fieldName, profile, "null-value");
        }
        if (value.isBlank()) {
            throw new ShellArgumentViolationException(fieldName, profile, "blank-value");
        }
        checkLength(value, fieldName, profile);
        checkNullByte(value, fieldName, profile);
        checkUnsafeChars(value, fieldName, profile);
        checkPathTraversal(value, fieldName, profile);
        checkAllowList(value, fieldName, profile);
    }

    /**
     * Validates a shell-bound argument only if a value is present.
     * Skips all checks when {@code value} is {@code null} or blank.
     * Use for optional fields that may be absent but must not be unsafe when present.
     *
     * @param value     the value to validate (skipped when null or blank)
     * @param fieldName the request field name, included in the exception for safe logging
     * @param profile   the allow-list profile to apply when the value is present
     * @throws ShellArgumentViolationException if the value is present and any safety check fails
     */
    public void validateIfPresent(String value, String fieldName, ShellArgumentProfile profile) {
        if (value == null || value.isBlank()) {
            return;
        }
        checkLength(value, fieldName, profile);
        checkNullByte(value, fieldName, profile);
        checkUnsafeChars(value, fieldName, profile);
        checkPathTraversal(value, fieldName, profile);
        checkAllowList(value, fieldName, profile);
    }

    private void checkLength(String value, String fieldName, ShellArgumentProfile profile) {
        if (value.length() > profile.getMaxLength()) {
            throw new ShellArgumentViolationException(fieldName, profile, "oversized-value");
        }
    }

    private void checkNullByte(String value, String fieldName, ShellArgumentProfile profile) {
        if (value.indexOf(NULL_BYTE) >= 0) {
            throw new ShellArgumentViolationException(fieldName, profile, "null-byte-injection");
        }
    }

    private void checkUnsafeChars(String value, String fieldName, ShellArgumentProfile profile) {
        for (char unsafe : UNSAFE_CHARS) {
            if (value.indexOf(unsafe) >= 0) {
                throw new ShellArgumentViolationException(fieldName, profile,
                        "command-separator-or-metacharacter");
            }
        }
    }

    private void checkPathTraversal(String value, String fieldName, ShellArgumentProfile profile) {
        if (value.contains(PATH_TRAVERSAL_SEQUENCE)) {
            throw new ShellArgumentViolationException(fieldName, profile, "path-traversal");
        }
    }

    private void checkAllowList(String value, String fieldName, ShellArgumentProfile profile) {
        if (!profile.getAllowListPattern().matcher(value).matches()) {
            throw new ShellArgumentViolationException(fieldName, profile, "allow-list-violation");
        }
    }
}
