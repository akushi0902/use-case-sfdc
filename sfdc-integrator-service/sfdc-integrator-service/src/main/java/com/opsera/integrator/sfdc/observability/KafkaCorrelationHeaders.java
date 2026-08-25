package com.opsera.integrator.sfdc.observability;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Helpers for propagating correlation identifiers through Kafka message headers.
 *
 * <p>Producers call {@link #addCorrelationHeader(ProducerRecord)} to stamp the
 * current MDC correlation identifier onto outgoing records.
 *
 * <p>Consumers call {@link #restoreFromRecord(ConsumerRecord)} before processing
 * and {@link #clearCorrelationId()} in a {@code finally} block afterward.
 *
 * <p>Missing or malformed headers are replaced with a generated UUID and a degraded
 * propagation event is logged — business message processing is never interrupted.
 *
 * <p>Existing business payload fields and Kafka topic names are not modified.
 */
public final class KafkaCorrelationHeaders {

    private static final Logger log = LoggerFactory.getLogger(KafkaCorrelationHeaders.class);

    /** Kafka header name carrying the correlation identifier. */
    public static final String HEADER_NAME = CorrelationIdConstants.HEADER_NAME;

    private KafkaCorrelationHeaders() {}

    /**
     * Stamps the current MDC correlation identifier onto a producer record header.
     * If MDC has no value, the header is not added (the consumer will generate a replacement).
     */
    public static void addCorrelationHeader(ProducerRecord<?, ?> record) {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add(HEADER_NAME, correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Extracts and validates the correlation identifier from a consumer record header.
     *
     * <p>Returns a safe validated value. Missing or malformed headers return a generated UUID;
     * a degraded propagation event is logged in that case.
     */
    public static String extractCorrelationId(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(HEADER_NAME);
        if (header == null || header.value() == null) {
            String generated = UUID.randomUUID().toString();
            log.warn("correlation-degraded: no {} header on record topic={} partition={} offset={} — generated={}",
                    HEADER_NAME, record.topic(), record.partition(), record.offset(), generated);
            return generated;
        }
        String raw = new String(header.value(), StandardCharsets.UTF_8);
        if (!isSafe(raw)) {
            String generated = UUID.randomUUID().toString();
            log.warn("correlation-degraded: malformed {} header on record topic={} partition={} offset={} — replaced with generated={}",
                    HEADER_NAME, record.topic(), record.partition(), record.offset(), generated);
            return generated;
        }
        return raw;
    }

    /**
     * Restores the correlation identifier from the consumer record into MDC.
     * Must be paired with {@link #clearCorrelationId()} in a {@code finally} block.
     */
    public static void restoreFromRecord(ConsumerRecord<?, ?> record) {
        String id = extractCorrelationId(record);
        MDC.put(CorrelationIdConstants.MDC_KEY, id);
    }

    /**
     * Removes the correlation identifier from MDC.
     * Call this in a {@code finally} block after listener processing to prevent
     * context leaking across pooled Kafka consumer threads.
     */
    public static void clearCorrelationId() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    /**
     * Returns {@code true} when the value passes the same safety checks applied at
     * HTTP ingress — within max length, printable ASCII only.
     */
    static boolean isSafe(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() > CorrelationIdConstants.MAX_LENGTH) {
            return false;
        }
        return CorrelationIdConstants.SAFE_PATTERN.matcher(value).matches();
    }
}
