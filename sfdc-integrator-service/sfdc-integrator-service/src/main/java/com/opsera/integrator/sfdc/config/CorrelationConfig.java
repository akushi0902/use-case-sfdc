package com.opsera.integrator.sfdc.config;

import com.opsera.integrator.sfdc.correlation.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@link CorrelationIdFilter} at the highest filter precedence.
 *
 * <p>Running at {@link Ordered#HIGHEST_PRECEDENCE} ensures that all controllers,
 * exception handlers, security filters, and future middleware components see an
 * MDC correlation identifier and that every response carries the canonical header.
 */
@Configuration
public class CorrelationConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("correlationIdFilter");
        return registration;
    }
}
