package com.bayflow.ops;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Every Bayflow service: the request id in logs and responses (see {@link RequestIds}). */
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
}
