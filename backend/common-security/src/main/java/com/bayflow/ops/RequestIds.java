package com.bayflow.ops;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One id per request, through every service it touches: nginx sets {@code X-Request-Id}; each
 * service puts it in the log context ({@code requestId}, on every log line), returns it in the
 * response, and passes it on when it calls another service. A request without one (a service
 * calling directly, a background job) gets a new id.
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** What nginx and the services send; anything else is replaced (it ends up in logs). */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private RequestIds() {
    }

    /** The current request's id, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Adds the current request's id to calls to other services. */
    public static ClientHttpRequestInterceptor propagate() {
        return (request, body, execution) -> {
            String id = current();
            if (id != null && !request.getHeaders().containsHeader(HEADER)) {
                request.getHeaders().set(HEADER, id);
            }
            return execution.execute(request, body);
        };
    }

    /** The servlet filter (registered first, by {@code ObservabilityAutoConfiguration}). */
    public static final class Filter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String given = request.getHeader(HEADER);
            String id = given != null && VALID.matcher(given).matches() ? given : UUID.randomUUID().toString();
            MDC.put(MDC_KEY, id);
            response.setHeader(HEADER, id);
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
