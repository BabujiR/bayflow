package com.bayflow.workshop.integration;

import org.springframework.web.client.RestClient;

import com.bayflow.http.ServiceHttp;
import com.bayflow.workshop.config.WorkshopProperties;

/** RestClients to other Bayflow services, with the configured timeouts (one shared JDK HTTP client). */
public final class HttpClients {

    private HttpClients() {
    }

    public static RestClient create(String baseUrl, WorkshopProperties properties) {
        return ServiceHttp.internal(baseUrl, properties.getConnectTimeout(), properties.getReadTimeout());
    }
}
