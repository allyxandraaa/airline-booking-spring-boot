package com.example.payment_service.payment.internal;

import com.example.payment_service.payment.TransientPaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConsumerFailureSimulator {

	private final int failuresBeforeSuccess;
	private final AtomicInteger attempts = new AtomicInteger();

	ConsumerFailureSimulator(@Value("${payment.consumer.failures-before-success:0}") int failuresBeforeSuccess) {
		this.failuresBeforeSuccess = failuresBeforeSuccess;
	}

	void failIfConfigured() {
		int attempt = attempts.incrementAndGet();
		if (attempt <= failuresBeforeSuccess) {
			throw new TransientPaymentException("Simulated transient failure, attempt " + attempt);
		}
	}

	public void reset() {
		attempts.set(0);
	}
}
