package com.opsera.integrator.sfdc.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SafeStructuredLogger} verifying allow-list enforcement,
 * sensitive-key filtering, null safety, and log-level mapping.
 *
 * <p>Uses a Logback {@code ListAppender} to capture emitted messages without
 * requiring a Spring context.
 */
class SafeStructuredLoggerTest {

    private SafeStructuredLogger safeLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        safeLogger = new SafeStructuredLogger();

        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SafeStructuredLogger.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
        logbackLogger.setLevel(Level.DEBUG);

        MDC.put(CorrelationIdConstants.MDC_KEY, "test-cid-001");
    }

    @AfterEach
    void tearDown() {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SafeStructuredLogger.class);
        logbackLogger.detachAppender(listAppender);
        listAppender.stop();
        MDC.clear();
    }

    // ── Log level mapping ──────────────────────────────────────────────────────

    @Test
    void logEvent_acceptedOutcome_logsAtInfoLevel() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("quick-deploy-start")
                .controller("JobExecutionController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logEvent_completedOutcome_logsAtInfoLevel() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.COMPLETED)
                .build());

        assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logEvent_rejectedOutcome_logsAtWarnLevel() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("quick-deploy-start")
                .controller("JobExecutionController")
                .outcome(SafeLogEvent.Outcome.REJECTED)
                .build());

        assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logEvent_failedOutcome_logsAtWarnLevel() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.FAILED)
                .build());

        assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    // ── Structured message content ────────────────────────────────────────────

    @Test
    void logEvent_messageContainsOperationControllerAndOutcome() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("quick-deploy-start")
                .controller("JobExecutionController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("op=quick-deploy-start");
        assertThat(message).contains("ctrl=JobExecutionController");
        assertThat(message).contains("outcome=ACCEPTED");
    }

    @Test
    void logEvent_correlationIdFromMdc_presentInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("post-refresh")
                .controller("PostRefreshTaskController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("cid=test-cid-001");
    }

    @Test
    void logEvent_correlationIdUnknown_whenMdcEmpty() {
        MDC.clear();
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("cid=unknown");
    }

    @Test
    void logEvent_safeFields_appearsInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("quick-deploy-start")
                .controller("JobExecutionController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("pipelineId", "pipeline-safe-001")
                .safeField("stepId", "step-safe-01")
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("pipelineId=pipeline-safe-001");
        assertThat(message).contains("stepId=step-safe-01");
    }

    // ── Sensitive key filtering ───────────────────────────────────────────────

    @Test
    void logEvent_tokenKeyFiltered_notInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("op")
                .controller("ctrl")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("apiToken", "secret-value-xyz")
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("apiToken");
        assertThat(message).doesNotContain("secret-value-xyz");
    }

    @Test
    void logEvent_passwordKeyFiltered_notInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("op")
                .controller("ctrl")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("userPassword", "hunter2")
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("userPassword");
        assertThat(message).doesNotContain("hunter2");
    }

    @Test
    void logEvent_orgUrlKeyFiltered_notInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("orgUrl", "https://myorg.salesforce.example.internal")
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("orgUrl");
        assertThat(message).doesNotContain("salesforce.example.internal");
    }

    @Test
    void logEvent_credentialsKeyFiltered_notInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("op")
                .controller("ctrl")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .safeField("credentials", "user:pass")
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("credentials");
    }

    // ── Allow-list builder: blank/null fields omitted ─────────────────────────

    @Test
    void safeLogEventBuilder_nullValue_fieldOmitted() {
        SafeLogEvent event = SafeLogEvent.builder()
                .operation("op")
                .safeField("pipelineId", null)
                .build();

        assertThat(event.getSafeFields()).doesNotContainKey("pipelineId");
    }

    @Test
    void safeLogEventBuilder_blankValue_fieldOmitted() {
        SafeLogEvent event = SafeLogEvent.builder()
                .operation("op")
                .safeField("pipelineId", "   ")
                .build();

        assertThat(event.getSafeFields()).doesNotContainKey("pipelineId");
    }

    @Test
    void safeLogEventBuilder_nullKey_fieldOmitted() {
        SafeLogEvent event = SafeLogEvent.builder()
                .operation("op")
                .safeField(null, "some-value")
                .build();

        assertThat(event.getSafeFields()).isEmpty();
    }

    // ── Null and degenerate safety ────────────────────────────────────────────

    @Test
    void logEvent_nullEvent_doesNotThrow() {
        assertThat(() -> safeLogger.logEvent(null)).doesNotThrowAnyException();
        assertThat(listAppender.list).isNotEmpty();
        assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logEvent_extractionFailed_flagAppearsInMessage() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("deploy")
                .controller("SfdcIntegratorController")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .extractionFailed(true)
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("extractionFailed=true");
    }

    @Test
    void logEvent_logInjectionAttempt_controlCharsSanitized() {
        safeLogger.logEvent(SafeLogEvent.builder()
                .operation("op\nnewline-inject")
                .controller("ctrl")
                .outcome(SafeLogEvent.Outcome.ACCEPTED)
                .build());

        String message = listAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("\n");
        assertThat(message).doesNotContain("\r");
    }
}
