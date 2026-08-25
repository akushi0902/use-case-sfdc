package com.opsera.integrator.sfdc.observability;

import com.opsera.integrator.sfdc.correlation.CorrelationIdConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CorrelationContextSnapshot} verifying context capture,
 * restoration on async threads, MDC cleanup, nested async behavior, and exception safety.
 */
class CorrelationContextSnapshotTest {

    private static final String TEST_CORRELATION_ID = "test-cid-abc-123";

    @BeforeEach
    void setUp() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @Test
    void capture_withMdcValue_capturesIt() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);

        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();

        assertThat(snapshot.getCorrelationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void capture_withNoMdcValue_capturesEmptyString() {
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();

        assertThat(snapshot.getCorrelationId()).isEmpty();
    }

    @Test
    void wrapRunnable_restoresCorrelationIdDuringExecution() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY); // simulate thread switch

        AtomicReference<String> capturedId = new AtomicReference<>();
        Runnable wrapped = snapshot.wrap(() -> capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY)));
        wrapped.run();

        assertThat(capturedId.get()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void wrapRunnable_clearsMdcAfterExecution() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        snapshot.wrap(() -> {}).run();

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void wrapRunnable_clearsMdcAfterException() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        Runnable throwing = snapshot.wrap(() -> { throw new RuntimeException("test error"); });

        assertThatThrownBy(throwing::run).isInstanceOf(RuntimeException.class);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void wrapRunnable_restoresPreviousMdcValueAfterExecution() {
        String previousId = "previous-cid-xyz";
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.put(CorrelationIdConstants.MDC_KEY, previousId); // different value on worker thread

        snapshot.wrap(() -> {}).run();

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isEqualTo(previousId);
    }

    @Test
    void wrapCallable_restoresCorrelationIdAndReturnsValue() throws Exception {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        AtomicReference<String> capturedId = new AtomicReference<>();
        String result = snapshot.wrap(() -> {
            capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY));
            return "ok";
        }).call();

        assertThat(result).isEqualTo("ok");
        assertThat(capturedId.get()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void wrapSupplier_restoresCorrelationIdAndReturnsValue() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        AtomicReference<String> capturedId = new AtomicReference<>();
        String result = snapshot.wrap(() -> {
            capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY));
            return "supplier-result";
        }).get();

        assertThat(result).isEqualTo("supplier-result");
        assertThat(capturedId.get()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void wrapRunnable_noCorrelationId_mdcRemainsAbsentDuringAndAfter() {
        // No MDC value when captured
        CorrelationContextSnapshot snapshot = CorrelationContextSnapshot.capture();

        AtomicReference<String> capturedId = new AtomicReference<>("sentinel");
        snapshot.wrap(() -> capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY))).run();

        assertThat(capturedId.get()).isNull();
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void nestedAsync_childRetainsSameCorrelationId() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CORRELATION_ID);
        CorrelationContextSnapshot outerSnapshot = CorrelationContextSnapshot.capture();
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        AtomicReference<String> innerCapturedId = new AtomicReference<>();

        Runnable outer = outerSnapshot.wrap(() -> {
            // Nested: inner snapshot captures the same value that outer restored
            CorrelationContextSnapshot innerSnapshot = CorrelationContextSnapshot.capture();
            innerSnapshot.wrap(() -> innerCapturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY))).run();
        });
        outer.run();

        assertThat(innerCapturedId.get()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }
}
