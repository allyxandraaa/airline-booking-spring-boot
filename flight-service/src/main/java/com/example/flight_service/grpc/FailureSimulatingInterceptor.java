package com.example.flight_service.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

class FailureSimulatingInterceptor implements ServerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(FailureSimulatingInterceptor.class);
	private static final String METHOD = "GetPrice";

	private final int failuresBeforeSuccess;
	private final AtomicInteger attempts = new AtomicInteger();

	FailureSimulatingInterceptor(int failuresBeforeSuccess) {
		this.failuresBeforeSuccess = failuresBeforeSuccess;
	}

	@Override
	public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
													   ServerCallHandler<Q, R> next) {
		if (!METHOD.equals(call.getMethodDescriptor().getBareMethodName())) {
			return next.startCall(call, headers);
		}
		int attempt = attempts.incrementAndGet();
		if (attempt <= failuresBeforeSuccess) {
			log.warn("{} attempt {}: UNAVAILABLE (simulated)", METHOD, attempt);
			call.close(Status.UNAVAILABLE.withDescription("Simulated failure, attempt " + attempt), new Metadata());
			return new ServerCall.Listener<>() {
			};
		}
		log.info("{} attempt {}: accepted", METHOD, attempt);
		return next.startCall(call, headers);
	}
}
