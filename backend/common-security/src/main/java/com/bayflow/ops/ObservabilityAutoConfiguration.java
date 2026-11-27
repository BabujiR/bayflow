package com.bayflow.ops;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/** Every Bayflow service: the request id in logs and responses (see {@link RequestIds}), and traces across services. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilityAutoConfiguration {

    @Bean
    FilterRegistrationBean<RequestIds.Filter> requestIdFilter() {
        FilterRegistrationBean<RequestIds.Filter> registration = new FilterRegistrationBean<>(new RequestIds.Filter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** Traces across services, when Micrometer Tracing is there (every service, via OpenTelemetry). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class Tracing {

        @Bean
        TracePropagation tracePropagation(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            return new TracePropagation(tracer, propagator);
        }
    }
}
