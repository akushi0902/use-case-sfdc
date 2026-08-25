package com.opsera.integrator.sfdc.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Listener-boundary tests for {@link KafkaCheckpointListener}.
 *
 * <p>Tests are performed via {@link KafkaCheckpointListener#processRecord} to avoid
 * requiring a live Kafka broker or embedded Kafka setup — the @KafkaListener annotation
 * wires to infrastructure we want to bypass in unit tests.
 *
 * <p>Verifies: valid event delegation, deserialization failure handling, blank message
 * handling, and that unexpected adapter errors do not throw from the listener.
 */
@DisplayName("KafkaCheckpointListener — listener-boundary tests")
class KafkaCheckpointListenerTest {

    private CheckpointPersistenceAdapter adapter;
    private KafkaCheckpointListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adapter = mock(CheckpointPersistenceAdapter.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        listener = new KafkaCheckpointListener(adapter, objectMapper);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("sfdc-job-checkpoints", 0, 0L, "key", value);
    }

    // ── Valid event delegation ────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid event delegation")
    class ValidEventDelegation {

        @Test
        @DisplayName("valid JSON checkpoint record is deserialized and delegated to adapter")
        void processRecord_validJson_delegatesToAdapter() {
            String json = """
                    {
                      "jobId": "job-listener-fixture-001",
                      "correlationId": "corr-listener-fixture-001",
                      "checkpointCode": "WORKER_STARTED",
                      "statusCategory": "RUNNING",
                      "sequenceNumber": 1,
                      "messageId": "msg-listener-fixture-001"
                    }
                    """;
            when(adapter.process(any())).thenReturn(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);

            listener.processRecord(record(json));

            verify(adapter).process(any(CheckpointEvent.class));
        }

        @Test
        @DisplayName("JSON with unknown additional fields is deserialized without error")
        void processRecord_unknownFields_ignoredAndDelegated() {
            String json = """
                    {
                      "jobId": "job-listener-fixture-002",
                      "checkpointCode": "WORKER_STARTED",
                      "statusCategory": "RUNNING",
                      "sequenceNumber": 1,
                      "unknownField": "some-value",
                      "anotherUnknownField": 42
                    }
                    """;
            when(adapter.process(any())).thenReturn(CheckpointPersistenceAdapter.ProcessingOutcome.PERSISTED);

            listener.processRecord(record(json));

            verify(adapter).process(any(CheckpointEvent.class));
        }
    }

    // ── Malformed/bad record handling ─────────────────────────────────────────

    @Nested
    @DisplayName("Malformed and bad record handling")
    class MalformedRecordHandling {

        @Test
        @DisplayName("non-JSON record value is skipped without adapter call and without exception")
        void processRecord_nonJsonValue_skippedNoException() {
            listener.processRecord(record("not-valid-json{{{"));
            verify(adapter, never()).process(any());
        }

        @Test
        @DisplayName("blank record value is skipped without adapter call")
        void processRecord_blankValue_skipped() {
            listener.processRecord(record("   "));
            verify(adapter, never()).process(any());
        }

        @Test
        @DisplayName("empty string record value is skipped without adapter call")
        void processRecord_emptyValue_skipped() {
            listener.processRecord(record(""));
            verify(adapter, never()).process(any());
        }

        @Test
        @DisplayName("unexpected adapter exception does not propagate from listener — loop is not poisoned")
        void processRecord_adapterThrows_exceptionCaughtNoRethrow() {
            String json = """
                    {
                      "jobId": "job-listener-fixture-003",
                      "checkpointCode": "WORKER_STARTED",
                      "sequenceNumber": 1
                    }
                    """;
            when(adapter.process(any())).thenThrow(new RuntimeException("unexpected adapter error"));

            // Must not throw
            listener.processRecord(record(json));
        }
    }
}
