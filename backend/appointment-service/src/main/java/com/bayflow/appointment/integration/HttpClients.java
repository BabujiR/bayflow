package com.bayflow.appointment.integration;

import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.http.ServiceHttp;

/** RestClients to other Bayflow services, with the configured timeouts (one shared JDK HTTP client). */
final class HttpClients {

    private HttpClients() {
    }

    static RestClient create(String baseUrl, IntegrationProperties properties) {
        return ServiceHttp.internal(baseUrl, properties.getConnectTimeout(), properties.getReadTimeout());
    }
}
