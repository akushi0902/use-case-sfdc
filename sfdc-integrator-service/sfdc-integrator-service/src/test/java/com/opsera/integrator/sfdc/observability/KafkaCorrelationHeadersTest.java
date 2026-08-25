package com.opsera.integrator.sfdc.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaCorrelationHeaders} verifying header addition, extraction,
 * malformed/missing header handling, MDC restoration, and cleanup.
 */
class KafkaCorrelationHeadersTest {

    private static final String TOPIC = "test-topic";
    private static final String TEST_CID = "kafka-cid-test-001";

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        Logger logger = (Logger) LoggerFactory.getLogger(KafkaCorrelationHeaders.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
        Logger logger = (Logger) LoggerFactory.getLogger(KafkaCorrelationHeaders.class);
        logger.detachAppender(listAppender);
    }

    // --- addCorrelationHeader ---

    @Test
    void addCorrelationHeader_withMdcValue_addsHeader() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CID);
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "key", "value");

        KafkaCorrelationHeaders.addCorrelationHeader(record);

        byte[] headerValue = record.headers().lastHeader(KafkaCorrelationHeaders.HEADER_NAME).value();
        assertThat(new String(headerValue, StandardCharsets.UTF_8)).isEqualTo(TEST_CID);
    }

    @Test
    void addCorrelationHeader_noMdcValue_doesNotAddHeader() {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "key", "value");

        KafkaCorrelationHeaders.addCorrelationHeader(record);

        assertThat(record.headers().lastHeader(KafkaCorrelationHeaders.HEADER_NAME)).isNull();
    }

    // --- extractCorrelationId ---

    @Test
    void extractCorrelationId_validHeader_returnsIt() {
        ConsumerRecord<String, String> record = consumerRecordWithHeader(TOPIC, TEST_CID);

        String id = KafkaCorrelationHeaders.extractCorrelationId(record);

        assertThat(id).isEqualTo(TEST_CID);
    }

    @Test
    void extractCorrelationId_missingHeader_generatesUuidAndLogsWarning() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "key", "value");

        String id = KafkaCorrelationHeaders.extractCorrelationId(record);

        assertThat(id).matches("[0-9a-f\\-]{36}");
        List<ILoggingEvent> warns = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warns).anyMatch(e -> e.getFormattedMessage().contains("correlation-degraded"));
    }

    @Test
    void extractCorrelationId_malformedHeader_generatesUuidAndLogsWarning() {
        ConsumerRecord<String, String> record = consumerRecordWithHeader(TOPIC,
                "bad value with spaces and <script>!");

        String id = KafkaCorrelationHeaders.extractCorrelationId(record);

        assertThat(id).matches("[0-9a-f\\-]{36}");
        List<ILoggingEvent> warns = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warns).anyMatch(e -> e.getFormattedMessage().contains("correlation-degraded"));
    }

    @Test
    void extractCorrelationId_oversizedHeader_generatesUuidAndLogsWarning() {
        String oversized = "a".repeat(CorrelationIdConstants.MAX_LENGTH + 1);
        ConsumerRecord<String, String> record = consumerRecordWithHeader(TOPIC, oversized);

        String id = KafkaCorrelationHeaders.extractCorrelationId(record);

        assertThat(id).matches("[0-9a-f\\-]{36}");
    }

    // --- restoreFromRecord / clearCorrelationId ---

    @Test
    void restoreFromRecord_setsCorrelationIdInMdc() {
        ConsumerRecord<String, String> record = consumerRecordWithHeader(TOPIC, TEST_CID);

        KafkaCorrelationHeaders.restoreFromRecord(record);

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isEqualTo(TEST_CID);
    }

    @Test
    void clearCorrelationId_removesFromMdc() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CID);

        KafkaCorrelationHeaders.clearCorrelationId();

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void restoreAndClear_lisenerPattern_mdcCleanedAfterProcessing() {
        ConsumerRecord<String, String> record = consumerRecordWithHeader(TOPIC, TEST_CID);

        KafkaCorrelationHeaders.restoreFromRecord(record);
        String duringProcessing = MDC.get(CorrelationIdConstants.MDC_KEY);
        KafkaCorrelationHeaders.clearCorrelationId();

        assertThat(duringProcessing).isEqualTo(TEST_CID);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    // --- isSafe ---

    @Test
    void isSafe_validUuid_returnsTrue() {
        assertThat(KafkaCorrelationHeaders.isSafe("550e8400-e29b-41d4-a716-446655440000")).isTrue();
    }

    @Test
    void isSafe_null_returnsFalse() {
        assertThat(KafkaCorrelationHeaders.isSafe(null)).isFalse();
    }

    @Test
    void isSafe_blank_returnsFalse() {
        assertThat(KafkaCorrelationHeaders.isSafe("   ")).isFalse();
    }

    @Test
    void isSafe_withControlCharacter_returnsFalse() {
        assertThat(KafkaCorrelationHeaders.isSafe("cid\ninjected")).isFalse();
    }

    // --- helpers ---

    private ConsumerRecord<String, String> consumerRecordWithHeader(String topic, String correlationId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, "key", "value");
        record.headers().add(KafkaCorrelationHeaders.HEADER_NAME,
                correlationId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
