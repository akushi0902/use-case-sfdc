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
 * Unit tests for {@link CorrelationTaskDecorator} verifying that it propagates MDC
 * correlation context to decorated tasks and always cleans up afterward.
 */
class CorrelationTaskDecoratorTest {

    private static final String TEST_CID = "decorator-test-cid-001";

    private CorrelationTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new CorrelationTaskDecorator();
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @Test
    void decorate_propagatesCorrelationIdToTask() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CID);

        AtomicReference<String> capturedId = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY)));
        MDC.remove(CorrelationIdConstants.MDC_KEY); // simulate thread switch before execution

        decorated.run();

        assertThat(capturedId.get()).isEqualTo(TEST_CID);
    }

    @Test
    void decorate_clearsMdcAfterTaskCompletes() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CID);

        Runnable decorated = decorator.decorate(() -> {});
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        decorated.run();

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void decorate_clearsMdcEvenWhenTaskThrows() {
        MDC.put(CorrelationIdConstants.MDC_KEY, TEST_CID);

        Runnable decorated = decorator.decorate(() -> { throw new RuntimeException("task failure"); });
        MDC.remove(CorrelationIdConstants.MDC_KEY);

        assertThatThrownBy(decorated::run).isInstanceOf(RuntimeException.class).hasMessage("task failure");
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void decorate_noCorrelationId_taskRunsWithNullMdc() {
        AtomicReference<String> capturedId = new AtomicReference<>("sentinel");
        Runnable decorated = decorator.decorate(() -> capturedId.set(MDC.get(CorrelationIdConstants.MDC_KEY)));

        decorated.run();

        assertThat(capturedId.get()).isNull();
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }
}
