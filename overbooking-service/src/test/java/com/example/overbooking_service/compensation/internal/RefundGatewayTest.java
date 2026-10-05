package com.example.overbooking_service.compensation.internal;

import com.example.overbooking_service.compensation.CompensationResult;
import com.example.overbooking_service.client.PaymentClient;
import com.example.overbooking_service.client.dto.RefundRequest;
import com.example.overbooking_service.client.dto.RefundResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "resilience4j.retry.instances.paymentClient.wait-duration=10ms")
@ActiveProfiles("test")
class RefundGatewayTest {

	private static final BigDecimal AMOUNT = new BigDecimal("120.50");

	@Autowired
	RefundGateway refundGateway;

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@MockitoBean
	PaymentClient paymentClient;

	private final UUID bookingId = UUID.randomUUID();

	@BeforeEach
	void resetCircuitBreaker() {
		circuitBreakerRegistry.circuitBreaker("paymentClient").reset();
	}

	@Test
	void serverErrorIsRetriedWithSameKeyThenSucceeds() {
		when(paymentClient.refund(anyString(), any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.thenReturn(new RefundResponse(UUID.randomUUID(), bookingId, AMOUNT, "SUCCESS"));

		CompensationResult result = refundGateway.refund(bookingId, AMOUNT, "key-1");

		assertThat(result).isEqualTo(new CompensationResult(bookingId, AMOUNT, "COMPLETED"));
		verify(paymentClient, times(3)).refund("key-1", new RefundRequest(bookingId, AMOUNT));
	}

	@Test
	void exhaustedRetriesFallBackToPending() {
		when(paymentClient.refund(anyString(), any()))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		CompensationResult result = refundGateway.refund(bookingId, AMOUNT, "key-1");

		assertThat(result.status()).isEqualTo("PENDING");
		verify(paymentClient, times(3)).refund("key-1", new RefundRequest(bookingId, AMOUNT));
	}

	@Test
	void clientErrorIsNotRetriedAndNotCountedAsFailure() {
		when(paymentClient.refund(anyString(), any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

		CompensationResult result = refundGateway.refund(bookingId, AMOUNT, "key-1");

		assertThat(result.status()).isEqualTo("PENDING");
		verify(paymentClient, times(1)).refund(anyString(), any());
		assertThat(circuitBreakerRegistry.circuitBreaker("paymentClient").getMetrics().getNumberOfFailedCalls()).isZero();
	}

	@Test
	void openCircuitSkipsPaymentServiceAndReturnsPending() {
		circuitBreakerRegistry.circuitBreaker("paymentClient").transitionToOpenState();

		CompensationResult result = refundGateway.refund(bookingId, AMOUNT, "key-1");

		assertThat(result.status()).isEqualTo("PENDING");
		verify(paymentClient, times(0)).refund(anyString(), any());
		assertThat(circuitBreakerRegistry.circuitBreaker("paymentClient").getState()).isEqualTo(CircuitBreaker.State.OPEN);
	}
}
