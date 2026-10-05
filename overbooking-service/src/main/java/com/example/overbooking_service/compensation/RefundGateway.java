package com.example.overbooking_service.compensation;

import com.example.overbooking_service.client.PaymentClient;
import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RefundGateway {

	private static final Logger log = LoggerFactory.getLogger(RefundGateway.class);

	private final PaymentClient paymentClient;

	public RefundGateway(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	@Retry(name = "paymentClient", fallbackMethod = "refundFallback")
	@CircuitBreaker(name = "paymentClient")
	@Bulkhead(name = "paymentClient", type = Bulkhead.Type.SEMAPHORE)
	public CompensationResult refund(UUID bookingId, BigDecimal amount, String idempotencyKey) {
		RefundResponse response = paymentClient.refund(idempotencyKey, new RefundRequest(bookingId, amount));
		String status = "SUCCESS".equals(response.status()) ? "COMPLETED" : "PENDING";
		return new CompensationResult(bookingId, amount, status);
	}

	CompensationResult refundFallback(UUID bookingId, BigDecimal amount,
									  String idempotencyKey, Throwable ex) {
		log.warn("Refund for booking {} deferred: {}", bookingId, ex.toString());
		return new CompensationResult(bookingId, amount, "PENDING");
	}
}
