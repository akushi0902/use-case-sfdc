package com.opsera.integrator.sfdc.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.integrator.sfdc.config.AppConfig;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import com.opsera.integrator.sfdc.exceptions.SfdcExceptionHandler;
import com.opsera.integrator.sfdc.governance.classification.ClassificationPolicyResolver;
import com.opsera.integrator.sfdc.controller.JobExecutionController;
import com.opsera.integrator.sfdc.logging.SafeStructuredLogger;
import com.opsera.integrator.sfdc.model.QuickDeployRequest;
import com.opsera.integrator.sfdc.service.QuickDeployService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-style tests verifying correlation propagation from an HTTP request
 * through async dispatch and into Kafka listener-style message handling.
 *
 * <p>Uses mocked service infrastructure — no real Salesforce, database, or Kafka broker.
 */
@WebMvcTest(controllers = JobExecutionController.class)
@Import({SfdcExceptionHandler.class, AppConfig.class})
class CorrelationPropagationIntegrationTest {

    private static final String TEST_CORRELATION_ID = "integration-cid-propagation-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("correlationAwareTaskExecutor")
    private Executor correlationAwareTaskExecutor;

    @MockBean
    private QuickDeployService quickDeployService;

    @MockBean
    private ClassificationPolicyResolver classificationPolicyResolver;

    @MockBean
    private SafeStructuredLogger safeStructuredLogger;

    @BeforeEach
    void setUp() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @Test
    void asyncDispatch_viaCorrelationAwareExecutor_preservesCorrelationId() throws Exception {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        AtomicReference<String> capturedId = new AtomicReference<>();
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                snapshot.wrap(() -> capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY))),
                correlationAwareTaskExecutor);
        future.get();

        assertThat(capturedId.get()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void httpRequest_withCorrelationHeader_serviceSeesCorrelationIdInMdcDuringExecution()
            throws Exception {
        AtomicReference<String> serviceSeenId = new AtomicReference<>();

        doAnswer(invocation -> {
            serviceSeenId.set(MDC.get(CorrelationIdConstants.MDC_KEY));
            return null;
        }).when(quickDeployService).start(any());

        QuickDeployRequest request = validQuickDeployRequest();

        mockMvc.perform(post("/quickdeploy")
                        .header(CorrelationIdConstants.HEADER_NAME, TEST_CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(serviceSeenId.get()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void kafkaListenerPattern_restoresCorrelationFromHeader_clearsMdcAfterProcessing() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "key", "value");
        record.headers().add(KafkaCorrelationHeaders.HEADER_NAME,
                TEST_CORRELATION_ID.getBytes(StandardCharsets.UTF_8));

        AtomicReference<String> duringProcessing = new AtomicReference<>();
        try {
            KafkaCorrelationHeaders.restoreFromRecord(record);
            duringProcessing.set(MDC.get(CorrelationIdConstants.MDC_KEY));
            // simulate listener work
        } finally {
            KafkaCorrelationHeaders.clearCorrelationId();
        }

        assertThat(duringProcessing.get()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void kafkaListenerPattern_missingHeader_generatesReplacementAndClearsMdc() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "key", "value");

        AtomicReference<String> duringProcessing = new AtomicReference<>();
        try {
            KafkaCorrelationHeaders.restoreFromRecord(record);
            duringProcessing.set(MDC.get(CorrelationIdConstants.MDC_KEY));
        } finally {
            KafkaCorrelationHeaders.clearCorrelationId();
        }

        assertThat(duringProcessing.get()).isNotBlank();
        assertThat(duringProcessing.get()).matches("[0-9a-f\\-]{36}");
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    private QuickDeployRequest validQuickDeployRequest() {
        QuickDeployRequest req = new QuickDeployRequest();
        req.setDeploymentRequestId("test-deploy-id");
        req.setPipelineId("pipeline-integration-001");
        req.setStepId("step-001");
        return req;
    }
}
