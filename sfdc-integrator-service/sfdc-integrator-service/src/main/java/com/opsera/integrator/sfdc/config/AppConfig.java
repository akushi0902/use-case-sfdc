package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.observability.CorrelationTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Application-level Spring configuration.
 *
 * <p>Registers a {@code ThreadPoolTaskExecutor} decorated with
 * {@link CorrelationTaskDecorator} so that any async work submitted through the
 * {@code correlationAwareTaskExecutor} bean automatically inherits the MDC
 * correlation identifier established at HTTP ingress.
 *
 * <p>Use this executor for {@code CompletableFuture.supplyAsync(task, executor)} calls
 * or inject it via {@code @Qualifier("correlationAwareTaskExecutor")} for
 * Spring {@code @Async} methods.
 */
@Configuration
public class AppConfig {

    @Bean(name = "correlationAwareTaskExecutor")
    public Executor correlationAwareTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("correlation-async-");
        executor.setTaskDecorator(new CorrelationTaskDecorator());
        executor.initialize();
        return executor;
    }
}
