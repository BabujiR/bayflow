package com.bayflow.appointment.integration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;

/** RestClients with the configured timeouts, so a slow service cannot hold a request open. */
final class HttpClients {

    private HttpClients() {
    }

    static RestClient create(String baseUrl, IntegrationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
