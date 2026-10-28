package com.example.payment_service.payment.internal;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
class OutboxTracing {

	static final String TRACEPARENT = "traceparent";

	private final Tracer tracer;
	private final Propagator propagator;

	OutboxTracing(Tracer tracer, Propagator propagator) {
		this.tracer = tracer;
		this.propagator = propagator;
	}

	String currentTraceparent() {
		Span span = tracer.currentSpan();
		if (span == null) {
			return null;
		}
		Map<String, String> carrier = new HashMap<>();
		propagator.inject(span.context(), carrier, Map::put);
		return carrier.get(TRACEPARENT);
	}

	<T> T call(String traceparent, Callable<T> action) throws Exception {
		if (traceparent == null) {
			return action.call();
		}
		Span span = propagator.extract(Map.of(TRACEPARENT, traceparent), Map::get).name("outbox relay").start();
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			return action.call();
		} finally {
			span.end();
		}
	}
}
