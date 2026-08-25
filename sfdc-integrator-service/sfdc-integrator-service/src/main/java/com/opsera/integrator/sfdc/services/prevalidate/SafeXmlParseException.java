package com.opsera.integrator.sfdc.services.prevalidate;

/**
 * Thrown by {@link SafeXmlParser} when package XML input cannot be safely parsed.
 *
 * <p>The message is a safe, template-driven string — it does not contain raw XML fragments,
 * credential values, or parser-internal stack trace content.
 *
 * <p>{@link #getErrorCode()} returns a stable machine-readable code suitable for use in
 * {@link PrevalidationFinding#getMessageCode()}.
 */
public final class SafeXmlParseException extends Exception {

    private final String errorCode;

    public SafeXmlParseException(String errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
