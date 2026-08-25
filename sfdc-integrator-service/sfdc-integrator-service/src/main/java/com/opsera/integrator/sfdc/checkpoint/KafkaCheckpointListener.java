package com.opsera.integrator.sfdc.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.observability.KafkaCorrelationHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Spring Kafka listener that consumes checkpoint progress events from the configured topic
 * and delegates to {@link CheckpointPersistenceAdapter} for durable persistence.
 *
 * <p>The listener is conditionally enabled via {@code sfdc.checkpoint.kafka.enabled=true}
 * so it can be disabled in test profiles or during the coexistence period without changing
 * existing Kafka configuration.
 *
 * <p>Error handling guarantees:
 * <ul>
 *   <li>Deserialization failures are caught, logged safely, and skipped — the listener loop
 *       is never poisoned by a bad message.</li>
 *   <li>Malformed, orphaned, and duplicate events are handled by the adapter — they do not
 *       cause listener restarts.</li>
 *   <li>Only safe operational fields are emitted to logs — no raw payload fragments,
 *       Salesforce credentials, or Kafka key values.</li>
 * </ul>
 *
 * <p>Correlation identifiers are restored from the {@code X-Correlation-Id} Kafka header
 * using {@link KafkaCorrelationHeaders} and cleared in a {@code finally} block.
 *
 * <p>Topic and group configuration:
 * <ul>
 *   <li>Topic: {@code sfdc.checkpoint.kafka.topic} (default: {@code sfdc-job-checkpoints})</li>
 *   <li>Group: {@code sfdc.checkpoint.kafka.consumer-group} (default: {@code sfdc-checkpoint-persistence})</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "sfdc.checkpoint.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaCheckpointListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaCheckpointListener.class);

    private final CheckpointPersistenceAdapter adapter;
    private final ObjectMapper objectMapper;

    @Value("${sfdc.checkpoint.kafka.topic:sfdc-job-checkpoints}")
    private String topic;

    public KafkaCheckpointListener(CheckpointPersistenceAdapter adapter,
                                   ObjectMapper objectMapper) {
        this.adapter = adapter;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an incoming checkpoint record from Kafka.
     *
     * <p>The record value is expected to be a UTF-8 JSON payload matching the
     * {@link CheckpointEvent} schema. Unknown additional fields are ignored.
     */
    @KafkaListener(
            topics = "${sfdc.checkpoint.kafka.topic:sfdc-job-checkpoints}",
            groupId = "${sfdc.checkpoint.kafka.consumer-group:sfdc-checkpoint-persistence}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCheckpointRecord(ConsumerRecord<String, String> record) {
        KafkaCorrelationHeaders.restoreFromRecord(record);
        try {
            processRecord(record);
        } finally {
            KafkaCorrelationHeaders.clearCorrelationId();
        }
    }

    void processRecord(ConsumerRecord<String, String> record) {
        if (record == null || record.value() == null || record.value().isBlank()) {
            log.warn("checkpoint-listener: received null or blank record value — skipped"
                    + " topic='{}' partition={} offset={}",
                    record != null ? record.topic() : "null",
                    record != null ? record.partition() : -1,
                    record != null ? record.offset() : -1);
            return;
        }

        CheckpointEvent event;
        try {
            event = objectMapper.readValue(record.value(), CheckpointEvent.class);
        } catch (Exception deserEx) {
            log.warn("checkpoint-listener: deserialization failed"
                    + " topic='{}' partition={} offset={} — skipped",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        try {
            CheckpointPersistenceAdapter.ProcessingOutcome outcome = adapter.process(event);
            log.debug("checkpoint-listener: processed jobId='{}' code='{}' outcome='{}'",
                    event.getJobId(), event.getCheckpointCode(), outcome);
        } catch (Exception processingEx) {
            log.warn("checkpoint-listener: unexpected error processing checkpoint"
                    + " jobId='{}' code='{}' error='{}'",
                    event.getJobId(), event.getCheckpointCode(), processingEx.getMessage());
            // Do not rethrow — prevents listener container from restarting for this partition
        }
    }
}
