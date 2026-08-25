package com.opsera.integrator.sfdc.logging;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Structured operational event logger that enforces an allow-list policy.
 *
 * <p>Emits INFO-level messages for accepted and completed operations, WARN for
 * rejected and failed outcomes. Never calls request DTO toString, never includes
 * raw request bodies, and never throws an exception that could interrupt request
 * processing. If an internal formatting failure occurs, a minimal safe fallback
 * event is emitted instead.
 *
 * <p>Sensitive key fragments are filtered from {@link SafeLogEvent#getSafeFields()}
 * as a second-pass defence; callers should still avoid adding sensitive values at
 * construction time.
 *
 * <p>Correlation ID is resolved from the event first; if absent, falls back to the
 * MDC value set by {@code CorrelationIdFilter}, defaulting to {@code "unknown"}.
 */
@Component
public class SafeStructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(SafeStructuredLogger.class);

    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password", "token", "secret", "apikey", "api_key", "credential",
            "credentials", "authorization", "cookie", "auth", "orgurl", "org_url",
            "sourceorgurl", "targetorgurl", "deploypackage", "payload"
    );

    /**
     * Emits a structured operational log event.
     *
     * <p>INFO for {@code ACCEPTED} and {@code COMPLETED} outcomes;
     * WARN for {@code REJECTED} and {@code FAILED} outcomes.
     * This method never throws.
     */
    public void logEvent(SafeLogEvent event) {
        try {
            if (event == null) {
                log.warn("safe-log-failure: null event received");
                return;
            }
            String correlationId = resolveCorrelationId(event);
            String message = buildMessage(event, correlationId);
            if (event.getOutcome() == SafeLogEvent.Outcome.ACCEPTED
                    || event.getOutcome() == SafeLogEvent.Outcome.COMPLETED) {
                log.info(message);
            } else {
                log.warn(message);
            }
        } catch (Exception e) {
            String op = event != null ? event.getOperation() : "unknown";
            String ctrl = event != null ? event.getController() : "unknown";
            log.warn("safe-log-failure op={} ctrl={}", op, ctrl);
        }
    }

    private String resolveCorrelationId(SafeLogEvent event) {
        String fromEvent = event.getCorrelationId();
        if (fromEvent != null && !fromEvent.isBlank()) {
            return sanitize(fromEvent);
        }
        String fromMdc = MDC.get(CorrelationIdConstants.MDC_KEY);
        return fromMdc != null && !fromMdc.isBlank() ? fromMdc : "unknown";
    }

    private String buildMessage(SafeLogEvent event, String correlationId) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("op=").append(sanitize(event.getOperation()));
        sb.append(" ctrl=").append(sanitize(event.getController()));
        sb.append(" cid=").append(correlationId);
        sb.append(" outcome=").append(event.getOutcome());

        if (event.isExtractionFailed()) {
            sb.append(" extractionFailed=true");
        } else {
            for (Map.Entry<String, String> entry : event.getSafeFields().entrySet()) {
                if (!isSensitiveKey(entry.getKey())) {
                    sb.append(" ").append(sanitize(entry.getKey()))
                      .append("=").append(sanitize(entry.getValue()));
                }
            }
        }
        return sb.toString();
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.toLowerCase().replace("-", "").replace("_", "");
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[\r\n\t\\x00-\\x1F]", "_");
    }
}
