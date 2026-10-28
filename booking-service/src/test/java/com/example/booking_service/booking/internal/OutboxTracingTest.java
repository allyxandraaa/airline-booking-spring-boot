package com.example.booking_service.booking.internal;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTracingTest {

	private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
			.setTracerProvider(SdkTracerProvider.builder().build())
			.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
			.build();
	private final io.opentelemetry.api.trace.Tracer otelTracer = sdk.getTracer("test");
	private final Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
	});
	private final OutboxTracing tracing = new OutboxTracing(tracer, new OtelPropagator(sdk.getPropagators(), otelTracer));

	@Test
	void withoutActiveSpanNothingIsStoredAndActionRunsDirectly() throws Exception {
		assertThat(tracing.currentTraceparent()).isNull();
		assertThat(tracing.call(null, () -> "sent")).isEqualTo("sent");
	}

	@Test
	void traceparentIsCapturedAndRestoredWithSameTraceId() throws Exception {
		Span span = tracer.nextSpan().name("http").start();
		String traceparent;
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			traceparent = tracing.currentTraceparent();
		} finally {
			span.end();
		}
		assertThat(traceparent).startsWith("00-" + span.context().traceId() + "-" + span.context().spanId() + "-");

		String restoredTraceId = tracing.call(traceparent, () -> tracer.currentSpan().context().traceId());

		assertThat(restoredTraceId).isEqualTo(span.context().traceId());
		assertThat(tracer.currentSpan()).isNull();
	}
}
