package com.opsera.integrator.sfdc.governance.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Safe metadata builder for audit events.
 *
 * <p>Only allow-listed keys can be added. Any key not in {@link #ALLOWED_KEYS}
 * is silently dropped with a warning — raw payloads, credentials, org URLs,
 * and bearer tokens must never appear in audit metadata.
 *
 * <p>Values are trimmed and truncated to {@link #MAX_VALUE_LENGTH} characters.
 * Values containing line-feed, carriage-return, or null characters are sanitized
 * to prevent log-injection via the stored metadata string.
 *
 * <p>Build with {@link #builder()} and call {@link Builder#toJson()} to obtain
 * the serialized metadata string for storage.
 */
public final class SafeAuditMetadata {

    private static final Logger log = LoggerFactory.getLogger(SafeAuditMetadata.class);

    static final int MAX_VALUE_LENGTH = 256;

    /** Keys permitted in audit metadata. All others are dropped. */
    static final Set<String> ALLOWED_KEYS = Set.of(
            "pipelineId",
            "stepId",
            "operationType",
            "outcome",
            "resourceRef",
            "requestSource"
    );

    private SafeAuditMetadata() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds a metadata field if the key is allow-listed and the value is non-null.
         * Silently ignores disallowed keys to prevent raw payload leakage.
         */
        public Builder field(String key, String value) {
            if (key == null || value == null) {
                return this;
            }
            if (!ALLOWED_KEYS.contains(key)) {
                log.warn("SafeAuditMetadata: dropping disallowed key '{}' — only allow-listed keys " +
                         "are permitted in audit metadata", key);
                return this;
            }
            fields.put(key, sanitize(value));
            return this;
        }

        /**
         * Serializes collected fields to a compact JSON object string.
         * Returns {@code null} when no allowed fields were added.
         */
        public String toJson() {
            if (fields.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append('"').append(escape(entry.getKey()))
                  .append("\":\"").append(escape(entry.getValue())).append('"');
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }

        private static String sanitize(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            // Remove log-injection characters
            String clean = trimmed
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .replace('\0', ' ');
            // Truncate to max length
            if (clean.length() > MAX_VALUE_LENGTH) {
                clean = clean.substring(0, MAX_VALUE_LENGTH);
            }
            return clean;
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
}
