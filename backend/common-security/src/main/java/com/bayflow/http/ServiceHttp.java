package com.bayflow.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bayflow.ops.RequestIds;

/**
 * HTTP clients for calls between Bayflow services and to outside APIs, on the JDK's
 * {@link HttpClient}: one per connect timeout for the whole process (it pools connections), HTTP/1.1,
 * its own work on virtual threads. Unlike {@code HttpURLConnection} it never sends a POST twice
 * on its own when a pooled connection turns out to be closed.
 */
public final class ServiceHttp {

    private static final Map<Duration, HttpClient> CLIENTS = new ConcurrentHashMap<>();

    private ServiceHttp() {
    }

    /** The process-wide client for this connect timeout. */
    static HttpClient client(Duration connectTimeout) {
        return CLIENTS.computeIfAbsent(connectTimeout, timeout -> HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                // Like HttpURLConnection: follow redirects, but never from https to http.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build());
    }

    /** A request factory: the shared client, and {@code readTimeout} for each whole response. */
    public static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client(connectTimeout));
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /** A client for another Bayflow service: request ids and trace context go along. */
    public static RestClient internal(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .requestInterceptor(RequestIds.propagate())
                .build();
    }

    /** A client for an outside API: nothing of ours (request ids, trace context) goes to it. */
    public static RestClient external(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
    }
}
