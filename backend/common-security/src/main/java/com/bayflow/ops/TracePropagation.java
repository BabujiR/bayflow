package com.bayflow.ops;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpRequest;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Puts the current trace into calls to other services ({@code traceparent}), so a request is
 * one trace across every service it touches. Used by {@link RequestIds#propagate()}, which every
 * internal client has; registered by {@link ObservabilityAutoConfiguration} when tracing is on.
 */
public final class TracePropagation {

    private static volatile TracePropagation current;

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public TracePropagation(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
        current = this;
    }

    /** Adds the trace headers for the current span, if there is one. */
    static void inject(HttpRequest request) {
        TracePropagation tracing = current;
        if (tracing == null) {
            return;
        }
        Tracer tracer = tracing.tracer.getIfAvailable();
        Propagator propagator = tracing.propagator.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null || propagator == null) {
            return;
        }
        propagator.inject(span.context(), request, (carrier, key, value) -> {
            if (carrier != null) {
                carrier.getHeaders().set(key, value);
            }
        });
    }
}
