package com.opsera.integrator.sfdc.correlation;

import java.util.regex.Pattern;

/**
 * Constants for HTTP correlation identifier handling.
 *
 * <p>The safe pattern restricts accepted identifiers to printable ASCII characters
 * that are safe in HTTP headers, logs, and downstream metadata. Control characters,
 * non-ASCII bytes, and other unsafe inputs are rejected and replaced with a
 * generated identifier — the unsafe inbound value is never copied to logs.
 */
public final class CorrelationIdConstants {

    private CorrelationIdConstants() {}

    /** HTTP request and response header carrying the correlation identifier. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /** MDC key under which the active correlation identifier is stored. */
    public static final String MDC_KEY = "correlationId";

    /**
     * Maximum accepted length for an inbound correlation identifier.
     * Values longer than this are treated as unsafe and replaced with a generated ID.
     */
    public static final int MAX_LENGTH = 64;

    /**
     * Safe pattern for accepted inbound correlation identifiers.
     * Permits: alphanumeric characters, hyphen, underscore, and dot.
     * Rejects: control characters, non-ASCII, spaces, special characters unsafe in logs or headers.
     */
    public static final Pattern SAFE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_.]{1,64}$");
}
