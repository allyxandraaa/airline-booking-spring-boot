package com.example.payment_service.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class FailureSimulatingInterceptor implements ServerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(FailureSimulatingInterceptor.class);
	private static final Set<String> METHODS = Set.of("RefundBatch", "ProcessRefunds");

	private final int failuresBeforeSuccess;
	private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

	FailureSimulatingInterceptor(int failuresBeforeSuccess) {
		this.failuresBeforeSuccess = failuresBeforeSuccess;
	}

	@Override
	public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
													   ServerCallHandler<Q, R> next) {
		String method = call.getMethodDescriptor().getBareMethodName();
		if (!METHODS.contains(method)) {
			return next.startCall(call, headers);
		}
		int attempt = attempts.computeIfAbsent(method, key -> new AtomicInteger()).incrementAndGet();
		if (attempt <= failuresBeforeSuccess) {
			log.warn("{} attempt {}: UNAVAILABLE (simulated)", method, attempt);
			call.close(Status.UNAVAILABLE.withDescription("Simulated failure, attempt " + attempt), new Metadata());
			return new ServerCall.Listener<>() {
			};
		}
		log.info("{} attempt {}: accepted", method, attempt);
		return next.startCall(call, headers);
	}
}
